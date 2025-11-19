package com.example.appcarrito;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log; // ¡IMPORTANTE: Se asegura este import para usar Log!
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    // 1. Variables de UI
    private EditText editTextExpeditionName;
    private Button buttonSettings, buttonForward, buttonBackward, buttonLeft, buttonRight, buttonStop;
    private ImageButton buttonCapturePhoto, buttonRecordVideo;
    private SeekBar seekBarServo;
    private TextView textViewStatus;
    private SurfaceView videoSurfaceView;

    // 2. Variables de Red TCP/IP (Movimiento y Servo)
    private String serverIP = "192.168.4.1";
    private int serverPort = 8080;

    private Socket tcpSocket;
    private PrintWriter output;
    private ExecutorService executorService; // Hilo de fondo para operaciones de red

    // 3. Variables de HTTP (Captura de Cámara)
    private int httpPort = 80;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar el Executor (siempre debe ser inicializado)
        executorService = Executors.newSingleThreadExecutor();

        // Inicializar todos los componentes de la UI
        initUI();

        // Asignar Listeners a los botones y controles
        setupListeners();
    }

    /**
     * Inicializa todas las vistas del XML por su ID.
     */
    private void initUI() {
        // Controles Superiores
        editTextExpeditionName = findViewById(R.id.editTextExpeditionName);
        buttonSettings = findViewById(R.id.buttonSettings);

        // Controles de Movimiento
        buttonForward = findViewById(R.id.buttonForward);
        buttonBackward = findViewById(R.id.buttonBackward);
        buttonLeft = findViewById(R.id.buttonLeft);
        buttonRight = findViewById(R.id.buttonRight);
        buttonStop = findViewById(R.id.buttonStop);

        // Controles de Cámara
        seekBarServo = findViewById(R.id.seekBarServo);
        buttonCapturePhoto = findViewById(R.id.imageButtonCapturePhoto);
        buttonRecordVideo = findViewById(R.id.buttonRecordVideo);

        // Estatus y Video
        textViewStatus = findViewById(R.id.textViewStatus);
        videoSurfaceView = findViewById(R.id.videoSurfaceView);

        // Establecer un mensaje inicial de nombre de expedición
        editTextExpeditionName.setText("Mision_01");
    }

    /**
     * Configura los Listeners de eventos para los botones y SeekBar.
     */
    private void setupListeners() {
        // --- Botón de Configuración de Conexión ---
        buttonSettings.setOnClickListener(v -> showConnectionDialog());

        // --- Controles de Movimiento (Comandos TCP/IP) ---
        buttonForward.setOnClickListener(v -> sendCommand("F"));
        buttonBackward.setOnClickListener(v -> sendCommand("B"));
        buttonLeft.setOnClickListener(v -> sendCommand("L"));
        buttonRight.setOnClickListener(v -> sendCommand("R"));
        buttonStop.setOnClickListener(v -> sendCommand("S"));

        // --- Controles de Cámara (Peticiones HTTP) ---
        buttonCapturePhoto.setOnClickListener(v -> sendHttpCaptureCommand("photo"));
        buttonRecordVideo.setOnClickListener(v -> sendHttpCaptureCommand("video_toggle"));

        // --- Control de Servo ---
        seekBarServo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Envía el ángulo del servo (progress) como comando TCP
                if (fromUser) {
                    sendCommand("A" + progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Opcional: forzar el envío del último valor al soltar
                sendCommand("A" + seekBar.getProgress());
            }
        });
    }

    // ==========================================================
    // LÓGICA DE CONEXIÓN Y COMUNICACIÓN TCP (Movimiento y Servo)
    // ==========================================================

    /**
     * Muestra un diálogo para que el usuario ingrese la IP y los Puertos.
     */
    private void showConnectionDialog() {
        // Se usa el layout dialog_connection.xml
        final LayoutInflater inflater = getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_connection, null);

        // Se inicializan los campos del diálogo
        final EditText inputIP = dialogView.findViewById(R.id.inputIP);
        final EditText inputPort = dialogView.findViewById(R.id.inputPort);
        final EditText inputHttpPort = dialogView.findViewById(R.id.inputHttpPort);

        // Precargar valores
        inputIP.setText(serverIP);
        inputPort.setText(String.valueOf(serverPort));
        inputHttpPort.setText(String.valueOf(httpPort));

        new AlertDialog.Builder(this)
                .setTitle("Configuración de Conexión")
                .setView(dialogView)
                .setPositiveButton("Conectar", (dialog, which) -> {
                    String ip = inputIP.getText().toString();
                    String portStr = inputPort.getText().toString();
                    String httpPortStr = inputHttpPort.getText().toString();

                    if (!ip.isEmpty() && !portStr.isEmpty() && !httpPortStr.isEmpty()) {
                        try {
                            serverIP = ip;
                            serverPort = Integer.parseInt(portStr);
                            httpPort = Integer.parseInt(httpPortStr);
                            connectToServer();
                        } catch (NumberFormatException e) {
                            Toast.makeText(MainActivity.this, "Puerto(s) inválido(s)", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Todos los campos son requeridos", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel())
                .show();
    }

    /**
     * Inicia el intento de conexión TCP en un hilo de fondo.
     */
    private void connectToServer() {
        updateStatus("Intentando conectar a: " + serverIP + ":" + serverPort + "...");

        executorService.execute(() -> {
            try {
                // Cerrar conexión previa si existe
                if (tcpSocket != null && !tcpSocket.isClosed()) {
                    tcpSocket.close();
                }

                // Intentar nueva conexión
                tcpSocket = new Socket(serverIP, serverPort);
                output = new PrintWriter(tcpSocket.getOutputStream());

                // Notificar éxito en el hilo principal
                runOnUiThread(() -> {
                    updateStatus("Conexión TCP exitosa.");
                    Toast.makeText(MainActivity.this, "Conectado al Carrito!", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                Log.e("WIFI_CONTROL", "Error de conexión TCP: " + e.getMessage());
                // Notificar error en el hilo principal
                runOnUiThread(() -> {
                    updateStatus("ERROR: Conexión TCP fallida.");
                    Toast.makeText(MainActivity.this, "Error de Conexión: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Envía un comando de texto al servidor TCP (ESP32).
     * @param command El comando a enviar (ej. "F", "S", "A90").
     */
    private void sendCommand(final String command) {
        if (output != null) {
            executorService.execute(() -> {
                try {
                    output.write(command);
                    output.flush();
                    Log.d("WIFI_CONTROL", "Comando TCP enviado: " + command);
                } catch (Exception e) {
                    Log.e("WIFI_CONTROL", "Error al enviar comando TCP: " + e.getMessage());
                    runOnUiThread(() -> updateStatus("Error de envío. ¿Desconectado?"));
                }
            });
        } else {
            Toast.makeText(this, "Por favor, conecta primero.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Actualiza el TextView de estado en el hilo principal.
     * @param message El mensaje de estado.
     */
    private void updateStatus(String message) {
        runOnUiThread(() -> textViewStatus.setText("Estatus: " + message));
    }

    // LÓGICA DE COMUNICACIÓN HTTP (Captura de Fotos/Video)

    /**
     * Envía una petición HTTP GET para comandos de la cámara (foto/video).
     * @param action La acción deseada ("photo" o "video_toggle").
     */
    private void sendHttpCaptureCommand(String action) {
        String filenameBase = editTextExpeditionName.getText().toString();
        String commandPath = "";

        if (action.equals("photo")) {
            commandPath = "/capture?type=photo&name=" + filenameBase;
            Toast.makeText(this, "Comando Foto enviado: " + filenameBase, Toast.LENGTH_SHORT).show();
        } else if (action.equals("video_toggle")) {
            commandPath = "/record"; // Endpoint simple para iniciar/detener
            Toast.makeText(this, "Comando Video enviado.", Toast.LENGTH_SHORT).show();
        }

        final String fullUrl = "http://" + serverIP + ":" + httpPort + commandPath; // Usamos final

        if (!fullUrl.isEmpty()) {
            // Ejecutar la petición HTTP en el Executor
            // ATENCIÓN: Esta sección es una SIMULACIÓN. Para hacer peticiones HTTP reales
            // en Android, debes usar una librería moderna como OkHttp.
            executorService.execute(() -> {
                try {
                    // --- AQUÍ IRÍA EL CÓDIGO REAL DE LA LIBRERÍA HTTP ---

                    // SIMULACIÓN
                    Thread.sleep(100); // Pequeño retardo para simular la red
                    Log.i("HTTP_CONTROL", "Petición GET simulada a: " + fullUrl);

                    // --- FIN SIMULACIÓN ---

                } catch (InterruptedException e) {
                    // Manejar interrupción
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    Log.e("HTTP_CONTROL", "Error en petición HTTP simulada: " + e.getMessage());
                }
            });
        }
    }

    // 4. Gestión del Ciclo de Vida
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cierra los recursos de red y el Executor
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
                // Ignorar si ya está cerrado
            }
        }
        updateStatus("Recursos liberados.");
    }
}