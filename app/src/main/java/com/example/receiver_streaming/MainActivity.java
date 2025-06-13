package com.example.receiver_streaming;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class MainActivity extends AppCompatActivity {
    private static final String MCAST_ADDR = "224.1.1.1";
    private static final int    MCAST_PORT = 5004;

    private MulticastSocket socket;
    private WifiManager.MulticastLock multicastLock;
    private ImageView imageView;
    private Thread receiverThread;
    private volatile boolean running = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Ajustar padding por sistema de barras
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Referencia al ImageView del layout
        imageView = findViewById(R.id.imageView);

        // Adquirir MulticastLock para recibir multicast en Wi-Fi
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        multicastLock = wm.createMulticastLock("mcastLock");
        multicastLock.setReferenceCounted(true);
        multicastLock.acquire();

        // Iniciar hilo de recepción de MJPEG por multicast
        receiverThread = new Thread(this::receiveLoop);
        receiverThread.start();
    }

    private void receiveLoop() {
        try {
            socket = new MulticastSocket(MCAST_PORT);
            InetAddress group = InetAddress.getByName(MCAST_ADDR);
            socket.joinGroup(group);

            byte[] buf = new byte[65507];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                byte[] jpeg = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), 0, jpeg, 0, packet.getLength());

                Bitmap bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                runOnUiThread(() -> imageView.setImageBitmap(bmp));
            }

            socket.leaveGroup(group);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;

        // Cerrar socket
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        // Liberar el lock
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
    }
}
