/*
 * Copyright 2018 École des Mines de Saint-Étienne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.thesmartenergy.sparql.generate.ws;

import com.github.thesmartenergy.sparql.generate.jena.cli.Response;
import com.google.gson.Gson;

import javax.websocket.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author maxime.lefrancois
 */
public class SessionManager {

    private final List<Response> responses = new ArrayList<>();

    private static final Gson gson = new Gson();

    final ScheduledExecutorService service;

    private Session session;

    public SessionManager() {
        service = Executors.newScheduledThreadPool(1);
        service.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                send();
            }
        }, 0, 200, TimeUnit.MILLISECONDS);  // execute every x seconds
    }

    void appendResponse(Response response) {
        responses.add(response);
        if (responses.size() > 1000) {
            send();
        }
    }

    public void setSession(Session session) {
        this.session = session;
    }

    private void send() {
        if (session != null && !responses.isEmpty() && session.isOpen()) {
            System.out.println(responses.size());
            try {
                session.getBasicRemote().sendText(gson.toJson(responses));
                responses.clear();
            } catch (IOException ex) {
                System.out.println(ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    void stop() {
        send();
    }
}
