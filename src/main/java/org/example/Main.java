package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Main extends Application {

    private static final int PORT = 12345;
    private static final int WIDTH = 500;
    private static final int HEIGHT = 500;
    private static final int MOVE_AMOUNT = 10;

    private double offsetX = 0;
    private double offsetY = 0;

    private GraphicsContext gc;
    private final List<LineSegment> lineSegments = new ArrayList<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        Pane root = new Pane(canvas);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        scene.setOnKeyPressed(this::handleKeyPress);

        primaryStage.setScene(scene);
        primaryStage.setTitle("Drawing Server");
        primaryStage.show();

        new Thread(this::startServer).start();
    }

    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                handler.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleKeyPress(KeyEvent event) {
        switch (event.getCode()) {
            case UP -> offsetY += MOVE_AMOUNT;
            case DOWN -> offsetY -= MOVE_AMOUNT;
            case LEFT -> offsetX += MOVE_AMOUNT;
            case RIGHT -> offsetX -= MOVE_AMOUNT;
        }
        redraw();
    }

    private void addLineSegment(LineSegment segment) {
        synchronized (lineSegments) {
            lineSegments.add(segment);
            Platform.runLater(this::redraw);
        }
    }

    private void redraw() {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.setFill(Color.BLACK);
        gc.fillText("Offset: (" + offsetX + ", " + offsetY + ")", 10, 20);

        synchronized (lineSegments) {
            for (LineSegment segment : lineSegments) {
                gc.setStroke(segment.color);
                gc.strokeLine(segment.x1 + offsetX, segment.y1 + offsetY, segment.x2 + offsetX, segment.y2 + offsetY);
            }
        }
    }

    private static class LineSegment {
        double x1, y1, x2, y2;
        Color color;

        LineSegment(double x1, double y1, double x2, double y2, Color color) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.color = color;
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private final Main mainInstance;
        private Color currentColor = Color.BLACK;

        public ClientHandler(Socket socket, Main mainInstance) {
            this.clientSocket = socket;
            this.mainInstance = mainInstance;
        }

        @Override
        public void run() {
            try (var in = clientSocket.getInputStream();
                 var reader = new java.util.Scanner(in)) {

                while (reader.hasNextLine()) {
                    String message = reader.nextLine();
                    if (message.matches("^[0-9A-Fa-f]{6}$")) {
                        currentColor = Color.web("#" + message);
                    } else if (message.matches("^-?\\d+\\.?\\d* -?\\d+\\.?\\d* -?\\d+\\.?\\d* -?\\d+\\.?\\d*$")) {
                        String[] parts = message.split(" ");
                        double x1 = Double.parseDouble(parts[0]);
                        double y1 = Double.parseDouble(parts[1]);
                        double x2 = Double.parseDouble(parts[2]);
                        double y2 = Double.parseDouble(parts[3]);

                        LineSegment segment = new LineSegment(x1, y1, x2, y2, currentColor);
                        mainInstance.addLineSegment(segment);
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}