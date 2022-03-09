package DemoOne;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

public class Server {
    public static void main(String[] args) throws IOException {
        try (ServerSocket listener = new ServerSocket(7000)) {
            System.out.println("The date server is running...");
            while (true) {
                try (Socket socket = listener.accept()) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    out.println(new Date().toString());
                }
            }
        }
    }
}
