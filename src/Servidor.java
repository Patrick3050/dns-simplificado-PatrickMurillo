import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Servidor {
    public static void main(String[] args) {
        int puerto = 5000;

        try (ServerSocket servidor = new ServerSocket(puerto)) {
            System.out.println("Servidor iniciado. Esperando conexión en el puerto " + puerto + "...");

            // Espera hasta que un cliente se conecte
            Socket cliente = servidor.accept();
            System.out.println("Cliente conectado desde: " + cliente.getInetAddress().getHostAddress());

            // Flujos de entrada/salida
            BufferedReader entrada = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
            PrintWriter salida = new PrintWriter(cliente.getOutputStream(), true);

            // Creamos un hashmap que toma el dominio como clave y una ArrayList de registro
            HashMap<String, ArrayList<Registro>> dirRegistros = new HashMap<>();
            String rutaRegistro = "src/registros.txt";

            // Leemos el fichero, y lo separamos en partes para dominio, tipo, valor.
            try (BufferedReader br = new BufferedReader(new FileReader(rutaRegistro))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] datos = linea.trim().split("\\s+");
                    Registro registro = new Registro(datos[0], datos[1], datos[2]);

                    // En esta condicion si no está la clave en el hashmap creamos y un arraylist
                    if (!dirRegistros.containsKey(datos[0])) {
                        dirRegistros.put(registro.getDominio(), new ArrayList<>());
                    }
                    // Si está dentro la clave, lo agregamos a su valor que es un arrayList
                    dirRegistros.get(registro.getDominio()).add(registro);
                }
            } catch (IOException e) {
                salida.println("500 Server error\n");
            }


            // Comunicación
            String mensaje;
            do {
                mensaje = entrada.readLine();

                String mensajeLimpio = mensaje.replaceAll("[^a-zA-Z0-9.\\s]", "").trim();

                // si el mensaje es EXIT o exit termina el servidor
                if (mensajeLimpio.equalsIgnoreCase("EXIT")) {
                    salida.println("Conexion terminada");
                } else {
                    if (mensajeLimpio.equals("LIST")) {
                        salida.println("150 Inicio listado");
                        for (Map.Entry<String, ArrayList<Registro>> datos : dirRegistros.entrySet()) {
                            ArrayList<Registro> valoresClave = datos.getValue();

                            for (int i = 0; i < valoresClave.size(); i++) {
                                salida.println(valoresClave.get(i).getDominio() + " " +
                                        valoresClave.get(i).getTipo() + " " +
                                        valoresClave.get(i).getValor());
                            }

                        }
                        salida.println("226 Fin listado\n");
                    }
                    else {
                        // Los dividimos por partes para que se pueda obtener la respuesta del servidor
                        String[] formato = mensajeLimpio.split("\\s+");

                        // si formato no tiene una longitud de 3, por los 3 valores divididos de "mensaje"
                        // y el primer valor no es LOOKUP, nos da el siguiente mensaje.
                        if (formato.length == 3 && formato[0].equals("LOOKUP")) {
                            String tipo = formato[1].trim();
                            String dominio = formato[2].trim();

                            // creamos un arraylist de registro que guarde el valor o los valores de la clave
                            ArrayList<Registro> registros = dirRegistros.get(dominio);
                            if (registros != null) {
                                boolean estaEncontrado = false;
                                for (int i = 0; i < registros.size(); i++) {
                                    // Si la segunda parte de formato es igual al arraylist en la posicion
                                    // actual dentro lo evaluamos por su tipo, y si lo encuentra
                                    // la variable booleana cambia a true.
                                    if (tipo.equals(registros.get(i).getTipo())) {
                                        salida.println("200 " + registros.get(i).getValor());
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

                            // abrimos el txt para agregar el nuevo registro.
                            try (FileWriter fw = new FileWriter(rutaRegistro, true)) {
                                Registro registro = new Registro(dominio, tipo, valor);

                                // 1. Si no está la clave, la creamos y la insertamos.
                                if (!dirRegistros.containsKey(dominio)) {
                                    dirRegistros.put(dominio, new ArrayList<>());
                                }
                                // 2. Obtener la lista (existente o recién creada). Ahora es segura (no es null).
                                ArrayList<Registro> registros = dirRegistros.get(dominio);


                                boolean esRepetido = false;

                                // hacemos un recorrido para no insertar valores duplicados en la misma clave
                                // del hasmap
                                for (int i = 0; i < registros.size(); i++) {
                                    if (tipo.equals(registros.get(i).getTipo()) && valor.equals(registros.get(i).getValor())) {
                                        esRepetido = true;
                                    }
                                }

                                if (!esRepetido) {
                                    fw.write("\n" + dominio + " " + tipo + " " + valor);
                                    salida.println("200 Record added\n");

                                    // Y agregamos a la lista que está en el HashMap.
                                    registros.add(registro);
                                } else {
                                    salida.println("400 Bad Request\n");
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
                System.out.println("Cliente dice: " + mensaje);

            } while (!mensaje.equalsIgnoreCase("EXIT"));

            // Cierre
            cliente.close();
        } catch (Exception e) {
            System.out.println("500 Server error\n");
        }
    }
}


