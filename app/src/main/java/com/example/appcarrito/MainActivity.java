package com.example.appcarrito;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log; // ¡IMPORTANTE: Se asegura este import para usar Log!
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.MediaRecorder;
import android.net.Uri;

import android.content.ContentValues;
import android.content.ContentResolver;
import android.provider.MediaStore;
import java.io.OutputStream;

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

    private Socket tcpSocket;//a cambiar
    private PrintWriter output;//a cambiar
    private ExecutorService executorService; // Hilo de fondo para operaciones de red

    // 3. Variables de HTTP (Captura de Cámara)
    private int httpPort = 81;

    private Vibrator vibrator;

    //para video
    private static final int SCREEN_RECORD_REQUEST_CODE = 1001;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String videoFilePath;

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

        //video
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

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
        webSettings.setLoadWithOverviewMode(true);//cambio
        textViewVideo = findViewById(R.id.textViewVideo);
        videoWebView.setWebViewClient(new WebViewClient());
        videoWebView.setLayerType(View.LAYER_TYPE_SOFTWARE, null); //linea para el video, quitar si va mas lento

        // Establecer un mensaje inicial de nombre de expedición
        editTextExpeditionName.setText("Expedición 1");
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
                    sendCommand("forward");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton
                    buttonForward.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    //comando para detener el carro
                    sendCommand("stop");
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
                    sendCommand("backward");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton y evia comando stop
                    buttonBackward.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    sendCommand("stop");
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
                    sendCommand("left");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton y detiene
                    buttonLeft.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    sendCommand("stop");
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
                    sendCommand("right");
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                    //se deja de presionar el boton y detiene
                    buttonRight.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    sendCommand("stop");
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
                sendCommand("stop");
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
        //sendHttpCaptureCommand("photo")
        buttonCapturePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonCapturePhoto.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        buttonCapturePhoto.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                    }
                }, 500);
                captureHighResPhoto();
            }
        });

        //startScreenRecording() -> alternativa para video
        //sendHttpCaptureCommand("video_toggle") -> opcion uno para video
        buttonRecordVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startScreenRecording();
                if (isRecording) {
                    buttonRecordVideo.setColorFilter(Color.RED);
                } else {
                    buttonRecordVideo.clearColorFilter();
                }
            }
        });

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
                            //connectToServer();
                            refreshStream();
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

    private void refreshStream() {
        // En el ESP32 CameraWebServer, el stream suele estar en el puerto 81
        String streamUrl = "http://" + serverIP + ":" + httpPort + "/stream";
        Log.d("APP_VIDEO", "Cargando stream: " + streamUrl);
        textViewVideo.setVisibility(View.GONE);
        videoWebView.loadUrl(streamUrl);
        updateStatus("Conectado a " + serverIP);
        buttonSettings.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
    }

     //Envía un comando de texto al servidor TCP (ESP32), el comando a enviar (ej. "F", "S", "A90").
    private void sendCommand(final String command) {
        // Construimos la URL: http://ip:80/action?go=forward
        String url = "http://" + serverIP + ":" + serverPort + "/action?go=" + command;

        Request request = new Request.Builder().url(url).build();

        // Ejecutamos la petición HTTP en segundo plano
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("HTTP_ERR", "Fallo al enviar comando: " + e.getMessage());
                runOnUiThread(() -> updateStatus("Error enviando comando"));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                // El ESP32 recibió el comando correctamente
                if (response.isSuccessful()) {
                    // Log.d("HTTP_OK", "Comando enviado: " + action);
                }
                response.close();
            }
        });
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
        executorService.shutdownNow();
    }

    private void captureScreenshotFromStream() {
        try {
            // 1. Crear el bitmap desde el WebView
            // Nota: getDrawingCache es obsoleto, pero si te funciona, lo dejamos así por ahora.
            videoWebView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(videoWebView.getDrawingCache());
            videoWebView.setDrawingCacheEnabled(false);

            if (bitmap == null) {
                Toast.makeText(this, "Error: No se pudo crear el bitmap", Toast.LENGTH_SHORT).show();
                return;
            }

            // 2. Definir el nombre y la carpeta personalizada
            String folderName = "MisExpediciones"; // <--- TU CARPETA PERSONALIZADA
            String filename = editTextExpeditionName.getText().toString() + "_foto_" + System.currentTimeMillis() + ".jpg";

            OutputStream fos;
            Uri imageUri = null;

            // 3. Lógica para versiones recientes (Android 10 / API 29 en adelante)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                // Aquí defines la ruta específica dentro de Pictures
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + folderName);

                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                fos = resolver.openOutputStream(imageUri);
            }
            // 4. Lógica para versiones antiguas (Android 9 e inferiores)
            else {
                String imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                File imageDir = new File(imagesDir, folderName);

                if (!imageDir.exists()) {
                    imageDir.mkdirs(); // Crea la carpeta si no existe
                }

                File image = new File(imageDir, filename);
                fos = new FileOutputStream(image);
            }

            // 5. Guardar la imagen
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
            fos.close();

            Toast.makeText(this, "Guardado en Imágenes/" + folderName, Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Log.e("APP_CAPTURE", "Error al guardar: " + e.getMessage());
            Toast.makeText(this, "Error al capturar imagen", Toast.LENGTH_SHORT).show();
        }
    }
    //metodos para grabar video video
    private void setupMediaRecorder() throws IOException {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        // Carpeta y nombre del archivo
        File folder = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Grabaciones");
        if (!folder.exists()) folder.mkdirs();

        videoFilePath = folder.getAbsolutePath() + "/video_" + System.currentTimeMillis() + ".mp4";
        mediaRecorder.setOutputFile(videoFilePath);
        mediaRecorder.setVideoSize(1280, 720);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mediaRecorder.setVideoFrameRate(30);

        mediaRecorder.prepare();
    }

    private void startScreenRecording() {
        if (!isRecording) {
            Intent captureIntent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, SCREEN_RECORD_REQUEST_CODE);
        } else {
            stopScreenRecording();
        }
    }

    private void stopScreenRecording() {
        try {
            isRecording = false;
            mediaRecorder.stop();
            mediaRecorder.reset();
            mediaProjection.stop();
            Toast.makeText(this, "Grabación guardada en: " + videoFilePath, Toast.LENGTH_LONG).show();

            // Opcional: abrir el video con el reproductor
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoFilePath), "video/mp4");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);

        } catch (Exception e) {
            Log.e("VIDEO_REC", "Error al detener grabación: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    setupMediaRecorder();
                    mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    MediaProjection.Callback callback = new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            stopScreenRecording();
                        }
                    };
                    mediaProjection.registerCallback(callback, null);

                    // Crear la superficie de grabación
                    Surface surface = mediaRecorder.getSurface();
                    VirtualDisplay display = mediaProjection.createVirtualDisplay(
                            "ScreenRecord",
                            getResources().getDisplayMetrics().widthPixels,
                            getResources().getDisplayMetrics().heightPixels,
                            getResources().getDisplayMetrics().densityDpi,
                            0,
                            surface,
                            null,
                            null
                    );

                    mediaRecorder.start();
                    isRecording = true;
                    Toast.makeText(this, "Grabando video...", Toast.LENGTH_SHORT).show();

                } catch (IOException e) {
                    Log.e("VIDEO_REC", "Error al preparar grabación: " + e.getMessage());
                }
            } else {
                Toast.makeText(this, "Permiso denegado para grabar pantalla", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //para tomar una captura de la foto desde el ESP32 y en HD
    private void captureHighResPhoto() {
        // 1. URL del endpoint de captura (Suele ser el puerto 80 o el 81 dependiendo de tu config)
        // En el ejemplo estándar de CameraWebServer, /capture suele estar en el puerto 80 (control),
        // pero a veces en el 81. Prueba con 'serverPort' (80) primero.
        String url = "http://" + serverIP + ":" + serverPort + "/capture";

        updateStatus("Solicitando foto HD...");
        Toast.makeText(this, "Capturando foto HD...", Toast.LENGTH_SHORT).show();

        Request request = new Request.Builder().url(url).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("FOTO_HD", "Error al descargar foto: " + e.getMessage());
                runOnUiThread(() -> {
                    updateStatus("Error al capturar foto");
                    Toast.makeText(MainActivity.this, "Error de conexión", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> updateStatus("Error del servidor: " + response.code()));
                    return;
                }
                // 2. Obtenemos los bytes de la imagen
                try (java.io.InputStream inputStream = response.body().byteStream()) {
                    // Convertimos el stream a Bitmap
                    final Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);

                    if (bitmap != null) {
                        // 3. Guardamos el Bitmap en el teléfono (usando tu lógica anterior)
                        saveBitmapToGallery(bitmap);
                    }
                } catch (Exception e) {
                    Log.e("FOTO_HD", "Error procesando imagen: " + e.getMessage());
                }
            }
        });
    }

    // Función auxiliar para guardar (Separada para mayor orden)
    private void saveBitmapToGallery(Bitmap bitmap) {
        String folderName = "MisExpediciones";
        String filename = editTextExpeditionName.getText().toString() + "_HD_" + System.currentTimeMillis() + ".jpg";

        try {
            OutputStream fos;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentResolver resolver = getContentResolver();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + folderName);
                fos = resolver.openOutputStream(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues));
            } else {
                File imageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName);
                if (!imageDir.exists()) imageDir.mkdirs();
                fos = new FileOutputStream(new File(imageDir, filename));
            }

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos); // Calidad 100
            fos.close();

            runOnUiThread(() -> {
                //updateStatus("Foto Guardada");
                Toast.makeText(MainActivity.this, "¡Foto Guardada en!", Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            Log.e("SAVE_IMG", "Error al guardar: " + e.getMessage());
        }
    }
}