/*
 * Copyright 2018 Ecole des Mines de Saint-Etienne.
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

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author maxime.lefrancois
 */
public class StopServlet extends HttpServlet {

    private static final Logger log = LoggerFactory.getLogger(StopServlet.class);

    public StopServlet() {
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        System.out.println("STOPPPPPPPPP");
        new Thread() {
            public void run() {
                try {
                    log.info("Shutting down the server...");
                    EventServer.server.stop();
                    log.info("Server has stopped.");
                } catch (Exception ex) {
                    log.error("Error when stopping Jetty server: " + ex.getMessage(), ex);
                }
            }
        }.start();
    }
}
