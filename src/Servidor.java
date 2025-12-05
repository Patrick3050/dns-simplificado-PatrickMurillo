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

            // Leemos el fichero, y lo separamos en partes para dominio, tipo, valor.
            try (BufferedReader br = new BufferedReader(new FileReader("src/registros.txt"))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    String[] datos = linea.split(" ");
                    Registro registro = new Registro(datos[0], datos[1], datos[2]);

                    // En esta condicion si no está la clave en el hashmap creamos y un arraylist
                    if(!dirRegistros.containsKey(datos[0])) {
                        dirRegistros.put(registro.getDominio(), new ArrayList<>());
                    }
                    // Si está dentro la clave, lo agregamos a su valor que es un arrayList
                    dirRegistros.get(registro.getDominio()).add(registro);
                }
            } catch (IOException e) {
                salida.println("500 Server error");
            }


            // Comunicación
            String mensaje;
            do {
                mensaje = entrada.readLine();

                // si el mensaje es EXIT o exit termina el servidor
                if (mensaje.equalsIgnoreCase("EXIT")) {
                    salida.println("Conexion terminada");
                }
                else {
                    if (mensaje.equals("LIST")) {
                        salida.println("150 Inicio listado");
                        for (Map.Entry<String, ArrayList<Registro>> datos : dirRegistros.entrySet()) {
                            ArrayList<Registro> valoresClave = datos.getValue();

                            for (int i = 0; i < valoresClave.size(); i++) {
                                salida.println(valoresClave.get(i).getDominio()+ " " +
                                               valoresClave.get(i).getTipo() + " " +
                                               valoresClave.get(i).getValor());
                            }

                        }
                        salida.println("226 Fin listado\n");
                    }
                    else {
                        // Los dividimos por partes para que se pueda obtener la respuesta del servidor
                        String[] formato = mensaje.split(" ");

                        // si formato no tiene una longitud de 3, por los 3 valores divididos de "mensaje"
                        // y el primer valor no es LOOKUP, nos da el siguiente mensaje.
                        if (formato.length == 3 && formato[0].equals("LOOKUP")) {

                            // creamos un arraylist de registro que guarde el valor o los valores de la clave
                            ArrayList<Registro> registros = dirRegistros.get(formato[2]);
                            if (registros != null) {
                                for (int i = 0; i < registros.size(); i++) {
                                    // Si la segunda y tercera parte de formato es igual al arraylist en la posicion
                                    // actual dentro lo evaluamos por su tipo y dominio respectivamente.
                                    if (formato[1].equals(registros.get(i).getTipo()) &&
                                            formato[2].equals(registros.get(i).getDominio())) {
                                        salida.println("200 " + registros.get(i).getValor());
                                    }
                                }
                                salida.println();
                            } else {
                                salida.println("404 Not Found\n");
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
        } catch (Exception e) {}
    }
}


