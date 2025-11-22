package com.example.appcarrito;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.InputType;
import android.util.Log; // ¡IMPORTANTE: Se asegura este import para usar Log!
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    // 1. Variables de UI
    private EditText editTextExpeditionName;
    private ImageButton buttonForward, buttonBackward, buttonLeft, buttonRight, buttonStop;
    private ImageButton buttonCapturePhoto, buttonRecordVideo, buttonSettings;
    private SeekBar seekBarServo;
    private TextView textViewStatus, textViewVideo;
    private WebView videoWebView;

    // 2. Variables de Red TCP/IP (Movimiento y Servo)
    private String serverIP = "10.102.191.71";
    private int serverPort = 80;

    private final OkHttpClient httpClient = new OkHttpClient();

    private Socket tcpSocket;
    private PrintWriter output;
    private ExecutorService executorService; // Hilo de fondo para operaciones de red

    // 3. Variables de HTTP (Captura de Cámara)
    private int httpPort = 81;

    private Vibrator vibrator;

    private void hacerVibrar(int duracionMs) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //para android 8 en adelante
                vibrator.vibrate(VibrationEffect.createOneShot(duracionMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                //para versiones anteriores
                vibrator.vibrate(duracionMs);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            //para android 12 en adelante
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager.getDefaultVibrator();
        } else {
            //solo para versiones anteriores
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        //verifica si tiene vibracion
        if (vibrator == null || !vibrator.hasVibrator()) {
            //por si no tiene vibes
            return;
        }

        // Inicializar el Executor (siempre debe ser inicializado)
        executorService = Executors.newSingleThreadExecutor();

        //los componentes
        initUI();

        // Asignar Listeners a los botones y controles
        setupListeners();
    }

     //Inicializa todas las vistas del XML por su ID.
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
        buttonStop.setColorFilter(Color.RED);

        // Controles de Cámara
        seekBarServo = findViewById(R.id.seekBarServo);
        buttonCapturePhoto = findViewById(R.id.imageButtonCapturePhoto);
        buttonRecordVideo = findViewById(R.id.buttonRecordVideo);

        // Estatus y Video
        textViewStatus = findViewById(R.id.textViewStatus);
        videoWebView = findViewById(R.id.videoWebView);
        WebSettings webSettings = videoWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        textViewVideo = findViewById(R.id.textViewVideo);
        videoWebView.setWebViewClient(new WebViewClient());

        // Establecer un mensaje inicial de nombre de expedición
        editTextExpeditionName.setText("Mision_01");
    }

     //Configura los Listeners de eventos para los botones y SeekBar.
    private void setupListeners() {

        //Botón de Configuración de Conexión
        //showConnectionDialog()
        buttonSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showConnectionDialog();
            }
        });

        //Controles de Movimiento (Comandos TCP/IP)
        //sendCommand("F")
        buttonForward.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    //se presiona el boton
                    buttonForward.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
                    //envia el comando de avanzar
                    hacerVibrar(50);
                    sendCommand("F");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton
                    buttonForward.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    //comando para detener el carro
                    sendCommand("S");
                }
                return true;
            }
        });
        //sendCommand("B")
        buttonBackward.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    //se presiona el boton y envia comando "atras"
                    buttonBackward.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
                    hacerVibrar(50);
                    sendCommand("B");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton y evia comando stop
                    buttonBackward.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    sendCommand("S");
                }
                return true;
            }
        });
        //sendCommand("L")
        buttonLeft.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    //se presiona el boton y envia comando hacia "izquierda"
                    buttonLeft.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
                    hacerVibrar(50);
                    sendCommand("L");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton y detiene
                    buttonLeft.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    sendCommand("S");
                }
                return true;
            }
        });
        //sendCommand("R")
        buttonRight.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    //se presiona el boton y envia comando hacia "derecha"
                    buttonRight.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_IN);
                    hacerVibrar(50);
                    sendCommand("R");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton y detiene
                    buttonRight.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    sendCommand("S");
                }
                return true;
            }
        });
        //para detener el carro
        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonStop.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                hacerVibrar(50);
                sendCommand("S");
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // Volver al color original (por ejemplo, blanco)
                        buttonStop.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
                    }
                }, 500);
            }
        });

        //Controles de Cámara (Peticiones HTTP)
        buttonCapturePhoto.setOnClickListener(v -> sendHttpCaptureCommand("photo"));
        buttonRecordVideo.setOnClickListener(v -> sendHttpCaptureCommand("video_toggle"));

        //Control de servo camara
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

    // LÓGICA DE CONEXIÓN Y COMUNICACIÓN TCP (Movimiento y Servo)

     //Muestra un diálogo para que el usuario ingrese la IP y los Puertos.
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

     //Inicia el intento de conexión TCP en un hilo de fondo.
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
                    buttonSettings.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
                    Toast.makeText(MainActivity.this, "Conectado al Carrito!", Toast.LENGTH_SHORT).show();
                    textViewVideo.setText("");
                    // carga video del ESP32
                    String streamUrl = "http://" + serverIP + ":81/stream";
                    System.out.println("URL del stream: " + streamUrl);
                    videoWebView.loadUrl(streamUrl);
                });
            } catch (IOException e) {
                Log.e("WIFI_CONTROL", "Error de conexión TCP: " + e.getMessage());
                // Notificar error en el hilo principal
                System.out.println("Error de conexión TCP: " + e.getMessage()); //ver error en consola
                runOnUiThread(() -> {
                    updateStatus("ERROR: Conexión TCP fallida.");
                    Toast.makeText(MainActivity.this, "Error de Conexión: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

     //Envía un comando de texto al servidor TCP (ESP32), el comando a enviar (ej. "F", "S", "A90").
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

     //Actualiza el TextView de estado en el hilo principal.El mensaje de estado.
    private void updateStatus(String message) {
        runOnUiThread(() -> textViewStatus.setText("Estatus: " + message));
    }

    // LÓGICA DE COMUNICACIÓN HTTP (Captura de Fotos/Video)

    //Envía una petición HTTP GET para comandos de la cámara (foto/video). La acción deseada ("photo" o "video_toggle").
    private void sendHttpCaptureCommand(String action) {
        String filenameBase = editTextExpeditionName.getText().toString();
        String commandPath = "";

        if (action.equals("photo")) {
            commandPath = "/capture?type=photo&name=" + filenameBase;
        } else if (action.equals("video_toggle")) {
            commandPath = "/record";
        }

        final String fullUrl = "http://" + serverIP + ":" + httpPort + commandPath;

        executorService.execute(() -> {
            try {
                Request request = new Request.Builder()
                        .url(fullUrl)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    Log.i("HTTP_CONTROL", "Respuesta: " + response.code());
                }
            } catch (Exception e) {
                Log.e("HTTP_CONTROL", "Error en petición HTTP: " + e.getMessage());
            }
        });
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
                // no hacer caso
            }
        }
        updateStatus("Recursos liberados.");
    }
}