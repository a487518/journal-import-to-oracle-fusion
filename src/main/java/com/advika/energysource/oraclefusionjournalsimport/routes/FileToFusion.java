package com.advika.energysource.oraclefusionjournalsimport.routes;

import com.advika.energysource.oraclefusionjournalsimport.beans.WebServiceClient;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


@Component
public class FileToFusion extends RouteBuilder {

    @Value("${input.journalfile.path}")
    private String fileWatcherPath;

    @Override
    public void configure() throws Exception {

        from("file:" + fileWatcherPath + "?" +
                "move=Archive/${file:name.noext}-${date:now:yyyyMMddHHmmssSSS}.${file:ext}" +
                "&moveFailed=Error/${file:name.noext}-${date:now:yyyyMMddHHmmssSSS}.${file:ext}")
                .routeId("file-watcher")
                .log("Successfully read the file from ${header.CamelFilePath}")
                .log("file name: ${headers.CamelFileName}")
                .bean(WebServiceClient.class,"client(${body},${headers.CamelFileName})");
    }
}
