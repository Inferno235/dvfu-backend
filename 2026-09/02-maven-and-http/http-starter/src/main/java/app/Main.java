package app;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import app.controller.ShopController;
import app.service.ShopService;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws Exception {

        System.setProperty("sun.net.httpserver.maxReqTime", "10");
        System.setProperty("sun.net.httpserver.maxRspTime", "10");

        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT), 0);

        ShopService shopService = new ShopService();

        server.createContext("/ping", new PingHandler());
        server.createContext("/", new ShopController(shopService));

        server.setExecutor(
                Executors.newVirtualThreadPerTaskExecutor());

        server.start();

        System.out.println(
                "Сервер запущен: http://localhost:" + PORT);
    }
}