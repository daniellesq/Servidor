import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class Chat {

    private static final int PORT = 8080;

    private static Socket socket;
    private static DataInputStream in;
    private static DataOutputStream out;
    private static Thread lector;
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("💬 Cliente de chat (concurrente)");
        imprimirAyuda();

        try {
            while (true) {
                System.out.print("🧑‍💻 Tú: ");
                String linea = sc.nextLine().trim();
                if (linea.isEmpty()) continue;

                if (linea.startsWith("start-conection")) {
                    String[] parts = linea.split("\\s+", 2);
                    if (parts.length < 2) {
                        System.out.println("Uso correcto: start-conection <IP>");
                        continue;
                    }
                    String ip = parts[1].trim();
                    try {
                        startConnection(ip);
                    } catch (IOException e) {
                        System.out.println("❌ No se pudo conectar: " + e.getMessage());
                    }
                } else if (linea.equalsIgnoreCase("salir")) {
                    try {
                        if (estaConectado()) out.writeUTF("salir");
                    } catch (IOException ignored) {}
                    cerrarSilencioso();
                    System.out.println("👋 Programa finalizado.");
                    break;
                } else if (linea.startsWith("change-userName")) {
                    String[] parts = linea.split("\\s+", 2);
                    if (parts.length < 2) {
                        System.out.println("Uso correcto: change-userName <nuevoNombre>");
                        continue;
                    }
                    try {
                        changeUserName(parts[1].trim());
                    } catch (Exception e) {
                        System.out.println("⚠️ " + e.getMessage());
                    }
                } else if (linea.startsWith("send-msg")) {
                    String[] parts = linea.split("\\s+", 3);
                    if (parts.length < 3) {
                        System.out.println("Uso correcto: send-msg <usuarioDestino> <mensaje>");
                        continue;
                    }
                    try {
                        sendPrivate(parts[1].trim(), parts[2]);
                    } catch (Exception e) {
                        System.out.println("⚠️ " + e.getMessage());
                    }
                } else if (linea.startsWith("global-msg")) {
                    String[] parts = linea.split("\\s+", 2);
                    if (parts.length < 2) {
                        System.out.println("Uso correcto: global-msg <mensaje>");
                        continue;
                    }
                    try {
                        sendGlobal(parts[1]);
                    } catch (Exception e) {
                        System.out.println("⚠️ " + e.getMessage());
                    }
                } else if (linea.equalsIgnoreCase("help")) {
                    imprimirAyuda();
                } else {
                    // Enviar tal cual si hay conexión
                    if (estaConectado()) {
                        try { out.writeUTF(linea); }
                        catch (IOException e) { System.out.println("⚠️ " + e.getMessage()); }
                    } else {
                        System.out.println("❓ Comando no reconocido. Escribe 'help' para ver las opciones.");
                    }
                }
            }
        } finally {
            cerrarSilencioso();
        }
    }

    // ===== Métodos pedidos en el enunciado (lado cliente) =====

    // start-conection: recibe IP, usa PORT = 8080
    private static void startConnection(String ip) throws IOException {
        if (estaConectado()) {
            System.out.println("✅ Ya estás conectado.");
            return;
        }
        socket = new Socket(ip, PORT);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        iniciarLector();
        System.out.println("✅ Conectado a " + ip + ":" + PORT);
        System.out.println("Comandos rápidos: change-userName, send-msg, global-msg, salir");
    }

    // change-userName <nuevoNombre>
    private static void changeUserName(String nuevo) throws IOException {
        exigirConexion();
        out.writeUTF("change-userName " + nuevo);
    }

    // send-msg <usuarioDestino> <mensaje>
    private static void sendPrivate(String usuarioDestino, String mensaje) throws IOException {
        exigirConexion();
        out.writeUTF("send-msg " + usuarioDestino + " " + mensaje);
    }

    // global-msg <mensaje>
    private static void sendGlobal(String mensaje) throws IOException {
        exigirConexion();
        out.writeUTF("global-msg " + mensaje);
    }

    // ===== utilidades cliente =====

    private static void iniciarLector() {
        lector = new Thread(() -> {
            try {
                while (true) {
                    String msg = in.readUTF();
                    System.out.println("\n💬 Servidor: " + msg);
                    System.out.print("🧑‍💻 Tú: ");
                }
            } catch (IOException e) {
                System.out.println("\n⚠️ Conexión terminada.");
            }
        });
        lector.setDaemon(true);
        lector.start();
    }

    private static boolean estaConectado() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    private static void exigirConexion() {
        if (!estaConectado()) throw new IllegalStateException("No conectado. Usa: start-conection <IP>");
    }

    private static void cerrarSilencioso() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private static void imprimirAyuda() {
        System.out.println("Comandos disponibles:");
        System.out.println("  start-conection <IP>                  # Conecta al servidor (puerto 8080)");
        System.out.println("  change-userName <nuevoNombre>         # Cambia tu nombre (sin espacios)");
        System.out.println("  send-msg <usuarioDestino> <mensaje>   # Envía mensaje privado");
        System.out.println("  global-msg <mensaje>                  # Envía mensaje global");
        System.out.println("  salir                                 # Cierra la sesión");
    }
}
