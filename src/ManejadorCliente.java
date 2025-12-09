import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
// Importaciones adicionales para sincronización si usas un contador simple

// Clase para manejar la comunicación con un único cliente en un hilo separado.
public class ManejadorCliente implements Runnable {
    private final Socket cliente;
    private final HashMap<String, ArrayList<Registro>> dirRegistros;
    private final int idCliente; // Variable para almacenar el ID
    private final String rutaRegistro = "src/registros.txt"; // Podrías pasarla como argumento

    public ManejadorCliente(Socket cliente, HashMap<String, ArrayList<Registro>> dirRegistros, int idCliente) {
        this.cliente = cliente;
        this.dirRegistros = dirRegistros;
        this.idCliente = idCliente;
    }

    @Override
    public void run() {
        System.out.println("Hilo de cliente ID " + idCliente + " iniciado para: " + cliente.getInetAddress().getHostAddress());
        try (
                // Flujos de entrada/salida
                BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
                PrintWriter salida = new PrintWriter(cliente.getOutputStream(), true);
        ) {
            // No es necesario recargar el archivo aquí, ya se cargó en el servidor
            // Lógica de comunicación (tu bucle do-while original)
            String mensaje;
            do {
                mensaje = entrada.readLine();
                if (mensaje == null) {
                    // Cliente cerró la conexión abruptamente
                    break;
                }

                String mensajeLimpio = mensaje.replaceAll("[^a-zA-Z0-9.\\s]", "").trim();

                // Si el mensaje es EXIT o exit termina la conexión con ESTE cliente.
                if (mensajeLimpio.equalsIgnoreCase("EXIT")) {
                    salida.println("Conexion terminada");
                } else {
                    // ... EL RESTO DE TU LÓGICA DE LIST, LOOKUP, REGISTER ...
                    // **IMPORTANTE**: Las operaciones que modifican `dirRegistros` (como REGISTER)
                    // deben estar sincronizadas para evitar corrupción de datos entre hilos.

                    if (mensajeLimpio.equals("LIST")) {
                        // Lógica de LIST (solo lectura, no necesita sincronización para el HashMap)
                        salida.println("150 Inicio listado");
                        synchronized (dirRegistros) { // Bloqueamos para asegurar una lectura consistente
                            for (Map.Entry<String, ArrayList<Registro>> datos : dirRegistros.entrySet()) {
                                ArrayList<Registro> valoresClave = datos.getValue();
                                for (int i = 0; i < valoresClave.size(); i++) {
                                    Registro registro = valoresClave.get(i);
                                    salida.println(registro.getDominio() + " " +
                                            registro.getTipo() + " " +
                                            registro.getValor());
                                }
                            }
                        }
                        salida.println("226 Fin listado\n");
                    }
                    else {
                        String[] formato = mensajeLimpio.split("\\s+");

                        if (formato.length == 3 && formato[0].equals("LOOKUP")) {
                            String tipo = formato[1].trim();
                            String dominio = formato[2].trim();

                            // Lógica de LOOKUP (solo lectura)
                            ArrayList<Registro> registros;
                            synchronized (dirRegistros) {
                                registros = dirRegistros.get(dominio);
                            }

                            if (registros != null) {
                                boolean estaEncontrado = false;
                                for (Registro registro : registros) {
                                    if (tipo.equals(registro.getTipo())) {
                                        salida.println("200 " + registro.getValor());
                                        estaEncontrado = true;
                                    }
                                }
                                if (!estaEncontrado) salida.println("404 Not Found\n");
                                else salida.println();
                            } else {
                                salida.println("404 Not Found\n");
                            }
                        }
                        else if (formato.length == 4 && formato[0].equals("REGISTER")) {
                            String dominio = formato[1].trim();
                            String tipo = formato[2].trim();
                            String valor = formato[3].trim();

                            // Lógica de REGISTER (modifica dirRegistros y archivo, NECESITA sincronización)
                            try {
                                synchronized (dirRegistros) { // Bloqueamos todo el HashMap
                                    ArrayList<Registro> registros = dirRegistros.get(dominio);
                                    Registro nuevoRegistro = new Registro(dominio, tipo, valor);

                                    boolean esRepetido = false;
                                    if (registros != null) {
                                        for (Registro registro : registros) {
                                            if (tipo.equals(registro.getTipo()) && valor.equals(registro.getValor())) {
                                                esRepetido = true;
                                                break;
                                            }
                                        }
                                    }

                                    if (!esRepetido) {
                                        // Escribir en el archivo (también debe ser sincronizado si varios hilos
                                        // pueden escribir simultáneamente, pero en este ejemplo lo hacemos dentro
                                        // del bloque sincronizado de dirRegistros para simplicidad)
                                        try (FileWriter fw = new FileWriter(rutaRegistro, true)) {
                                            fw.write("\n" + dominio + " " + tipo + " " + valor);
                                        }

                                        if (registros == null) {
                                            registros = new ArrayList<>();
                                            dirRegistros.put(dominio, registros);
                                        }
                                        registros.add(nuevoRegistro);
                                        salida.println("200 Record added\n");
                                    } else {
                                        salida.println("400 Bad Request\n");
                                    }
                                }
                            } catch (IOException e) {
                                salida.println("500 Server error\n");
                            }

                        }
                        else {
                            salida.println("400 Bad request\n");
                        }
                    }
                }
                System.out.println("Cliente " + idCliente + " dice (" + cliente.getInetAddress().getHostAddress() + "): " + mensaje);

            } while (!mensaje.equalsIgnoreCase("EXIT"));

        } catch (IOException e) {
            System.err.println("Error de I/O en la comunicación con el cliente ID " + idCliente + ": " + e.getMessage());        } finally {
            try {
                if (cliente != null && !cliente.isClosed()) {
                    cliente.close();
                }
            } catch (IOException e) {
                System.err.println("Error al cerrar el socket del cliente ID " + idCliente + ": " + e.getMessage());
            }
            System.out.println("Conexión con el cliente ID " + idCliente + " (" + cliente.getInetAddress().getHostAddress() + ") terminada.");

            // ** DECREMENTAR EL CONTADOR DE FORMA SEGURA **
            synchronized (Servidor.class) { // Usamos el mismo monitor que en Servidor.main
                Servidor.clientesActivos--;
                System.out.println("Clientes activos restantes: " + Servidor.clientesActivos);            }
        }
    }
}