import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private static final int PORT = 8080;
    // Mapa: nombreDeUsuario -> canal de salida
    private static final ConcurrentHashMap<String, DataOutputStream> clients = new ConcurrentHashMap<>();
    private static final AtomicInteger ID_GEN = new AtomicInteger(1);

    public static void main(String[] args) {
        try (ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("🖥️ Servidor escuchando en el puerto " + PORT);
            while (true) {
                Socket socket = server.accept();
                System.out.println("👤 Nuevo cliente desde " + socket.getInetAddress());
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            System.err.println("❌ Error en el servidor: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String userName;

        ClientHandler(Socket socket) {
            this.socket = socket;
            // Nombre por defecto único
            this.userName = "Usuario" + ID_GEN.getAndIncrement();
        }

        @Override
        public void run() {
            try {
                in  = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                clients.put(userName, out);
                sendSafe(out, "✅ Bienvenido. Tu usuario inicial es '" + userName + "'.");
                sendSafe(out, "Comandos: change-userName <nuevo>, send-msg <usuario> <mensaje>, global-msg <mensaje>, salir");
                sendToAll("🔔 " + userName + " se ha unido al chat.", out);

                while (true) {
                    String msg = in.readUTF();
                    if ("salir".equalsIgnoreCase(msg)) break;
                    processCommand(msg);
                }
            } catch (IOException e) {
                System.out.println("⚠️ Cliente desconectado: " + userName + " (" + socket.getInetAddress() + ")");
            } finally {
                clients.remove(userName);
                sendToAll("👋 " + userName + " salió del chat.", out);
                try { socket.close(); } catch (IOException ignored) {}
            }
        }

        private void processCommand(String msg) throws IOException {
            if (msg.startsWith("change-userName")) {
                String[] parts = msg.split("\\s+", 2);
                if (parts.length < 2) {
                    sendSafe(out, "❌ Uso: change-userName <nuevoNombre> (sin espacios)");
                    return;
                }
                String nuevo = parts[1].trim();
                if (nuevo.isEmpty() || nuevo.contains(" ")) {
                    sendSafe(out, "❌ Nombre inválido. Evita espacios y deja al menos un carácter.");
                    return;
                }
                DataOutputStream yaExiste = clients.get(nuevo);
                if (yaExiste != null && yaExiste != out) {
                    sendSafe(out, "❌ El nombre '" + nuevo + "' ya está en uso.");
                    return;
                }
                // Actualizar mapa y nombre
                clients.remove(userName);
                String anterior = userName;
                userName = nuevo;
                clients.put(userName, out);
                sendSafe(out, "✅ Nombre cambiado a: " + userName);
                sendToAll("🆔 " + anterior + " ahora es " + userName + ".", out);

            } else if (msg.startsWith("send-msg")) {
                // Forma: send-msg <usuarioDestino> <mensaje>
                String[] parts = msg.split("\\s+", 3);
                if (parts.length < 3) {
                    sendSafe(out, "❌ Uso: send-msg <usuarioDestino> <mensaje>");
                    return;
                }
                String destinatario = parts[1].trim();
                String cuerpo = parts[2];

                DataOutputStream canalDestino = clients.get(destinatario);
                if (canalDestino == null) {
                    sendSafe(out, "❌ Usuario no encontrado: " + destinatario);
                    return;
                }
                sendSafe(canalDestino, "✉️ [privado de " + userName + "]: " + cuerpo);
                sendSafe(out, "📨 Enviado a " + destinatario + ": " + cuerpo);
                System.out.println("📩 PRIVADO " + userName + " -> " + destinatario + ": " + cuerpo);

            } else if (msg.startsWith("global-msg")) {
                String[] parts = msg.split("\\s+", 2);
                if (parts.length < 2) {
                    sendSafe(out, "❌ Uso: global-msg <mensaje>");
                    return;
                }
                String cuerpo = parts[1];
                for (DataOutputStream client : clients.values()) {
                    if (client != out) sendSafe(client, "🌍 [" + userName + "]: " + cuerpo);
                }
                sendSafe(out, "✅ Mensaje global enviado.");
                System.out.println("📢 GLOBAL " + userName + ": " + cuerpo);

            } else {
                // Comando o texto no reconocido
                sendSafe(out, "ℹ️ Comando no reconocido. Usa: change-userName, send-msg, global-msg, salir");
                System.out.println("💬 (" + userName + "): " + msg);
            }
        }
    }

    // ===== utilidades servidor =====

    private static void sendToAll(String texto, DataOutputStream except) {
        for (DataOutputStream client : clients.values()) {
            if (client != except) sendSafe(client, texto);
        }
    }

    // Sincroniza los writes para evitar que dos hilos mezclen bytes en el mismo canal
    private static void sendSafe(DataOutputStream client, String texto) {
        synchronized (client) {
            try { client.writeUTF(texto); }
            catch (IOException ignored) {}
        }
    }
}
