import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Servidor {

    private static final int MAX_CLIENTES = 5;
    public static int clientesActivos = 0;

    // Contador de IDs global
    public static int proximoClienteId = 1;

    public static void main(String[] args) {
        int puerto = 5000;

        HashMap<String, ArrayList<Registro>> dirRegistros = new HashMap<>();
        String rutaRegistro = "src/registros.txt";

        try (BufferedReader br = new BufferedReader(new FileReader(rutaRegistro))) {
            // ... (Lógica de carga de registros) ...
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] datos = linea.trim().split("\\s+");
                if (datos.length >= 3) {
                    Registro registro = new Registro(datos[0], datos[1], datos[2]);
                    if (!dirRegistros.containsKey(datos[0])) {
                        dirRegistros.put(datos[0], new ArrayList<>());
                    }
                    dirRegistros.get(datos[0]).add(registro);
                }
            }
            System.out.println("Registros cargados exitosamente.");
        } catch (IOException e) {
            System.err.println("Error al cargar registros.txt: " + e.getMessage());
            return;
        }

        // Bucle principal para escuchar conexiones
        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado. Esperando conexiones en el puerto " + puerto + "...");

            while (true) {
                Socket cliente = servidor.accept();
                String clienteInfo = cliente.getInetAddress().getHostAddress();
                System.out.println("Intento de conexión de: " + clienteInfo);

                // CONTROL DE LÍMITE DE CLIENTES USANDO SINCRONIZACIÓN

                // 2. Sincronizamos el acceso a la variable clientesActivos.
                synchronized (Servidor.class) { // Usamos la clase Servidor como monitor
                    if (clientesActivos < MAX_CLIENTES) {
                        // Asignar el ID antes de la conexión
                        int idCliente = proximoClienteId++;

                        clientesActivos++; // Incremento seguro

                        System.out.println("Cliente ID " + idCliente + " conectado. Clientes activos: " + clientesActivos);

                        ManejadorCliente manejador = new ManejadorCliente(cliente, dirRegistros, idCliente);
                        Thread hiloCliente = new Thread(manejador);
                        hiloCliente.start();

                    } else {
                        // Rechazar la conexión
                        System.out.println("Conexión rechazada. Límite de " + MAX_CLIENTES + " clientes alcanzado.");
                        try (PrintWriter salida = new PrintWriter(cliente.getOutputStream(), true)) {
                            salida.println("503 Service Unavailable - Límite de clientes alcanzado.");
                        }
                        cliente.close();
                    }
                } // Fin del bloque synchronized
            }
        } catch (Exception e) {
            System.err.println("Error fatal del servidor: " + e.getMessage());
        }
    }
}