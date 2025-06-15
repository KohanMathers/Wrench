package me.kmathers.wrench;

import me.kmathers.wrench.network.TcpServer;

public class Wrench {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Hello, Wrench!");
        int port = 25565;
        TcpServer server = new TcpServer(port);
        server.start();
    }
}
