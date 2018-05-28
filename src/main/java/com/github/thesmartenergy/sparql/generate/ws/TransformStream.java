/*
 * Copyright 2017 École des Mines de Saint-Étienne.
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
import com.github.thesmartenergy.sparql.generate.jena.cli.Request;
import com.github.thesmartenergy.sparql.generate.jena.SPARQLGenerate;
import com.github.thesmartenergy.sparql.generate.jena.engine.PlanFactory;
import com.github.thesmartenergy.sparql.generate.jena.engine.RootPlan;
import com.github.thesmartenergy.sparql.generate.jena.query.SPARQLGenerateQuery;
import com.github.thesmartenergy.sparql.generate.jena.stream.LocatorFileAccept;
import com.github.thesmartenergy.sparql.generate.jena.stream.LookUpRequest;
import com.github.thesmartenergy.sparql.generate.jena.stream.SPARQLGenerateStreamManager;
import com.github.thesmartenergy.sparql.generate.jena.utils.SPARQLGenerateUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.util.EnumSet;
import java.util.Set;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import org.apache.commons.io.IOUtils;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Quad;
import org.apache.jena.sparql.serializer.SerializationContext;
import org.apache.jena.sparql.util.FmtUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.PatternLayout;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 *
 * @author maxime.lefrancois
 */
@ServerEndpoint("/transformStream")
public class TransformStream {

    private static final Logger LOG = LoggerFactory.getLogger(TransformStream.class);
    private static final Gson gson = new Gson();
    private final StringWriterAppender appender = (StringWriterAppender) org.apache.log4j.Logger.getRootLogger().getAppender("WEBSOCKET");
    private static final org.apache.log4j.Logger rootLogger = org.apache.log4j.Logger.getRootLogger();
    private static final Layout layout = new PatternLayout("%5p [%t] (%F:%L) - %m%n");

    static {
        SPARQLGenerate.init();
    }

    @OnOpen
    public void open(Session session) {
        LOG.info("Establishing connection");
    }

    @OnClose
    public void close(Session session) {
        LOG.info("Closing connection");
    }

    @OnMessage
    public void handleMessage(String message, Session session) throws IOException, InterruptedException {
        LOG.info("Handling transformation " + message);
        EventServer.manager.setSession(session);
        EventServer.manager.appendResponse(new Response("", "", true));

        Request request;

        Configuration conf;
        try {
            conf = gson.fromJson(message, Configuration.class);
        } catch (Exception ex) {
            stop(ex, "reading the parameters", EventServer.manager);
            return;
        }

        try {
            Level level = Level.toLevel(conf.log, Level.DEBUG);
            rootLogger.setLevel(level);
        } catch (Exception ex) {
            rootLogger.setLevel(Level.DEBUG);
        }
        
        String pwd = ".";
        Appender appender = null;
        try {
            pwd = conf.pwd != null ? conf.pwd : ".";
            appender = new org.apache.log4j.RollingFileAppender(layout, pwd + "/output.log", false);
            rootLogger.addAppender(appender);
        } catch (IOException ex) {
            stop(ex, "initializing the log file", EventServer.manager);
            rootLogger.removeAppender(appender);
            return;
        }

        File dir = new File(pwd);

        // read sparql-generate-conf.json
        try {
            String conffile = IOUtils.toString(new FileInputStream(new File(dir, "sparql-generate-conf.json")), "utf-8");
            request = gson.fromJson(conffile, Request.class);
        } catch (IOException | JsonSyntaxException ex) {
            LOG.warn(ex.getClass().getSimpleName() + " while loading the location mapping model for the queryset. No named queries will be used");
            request = Request.DEFAULT;
        }

        // initialize stream manager
        SPARQLGenerateStreamManager sm = SPARQLGenerateStreamManager.makeStreamManager(new LocatorFileAccept(dir.toURI().getPath()));
        sm.setLocationMapper(request.asLocationMapper());
        SPARQLGenerate.setStreamManager(sm);

        String queryPath = request.query != null ? request.query : "query.rqg";
        String query;
        try {
            query = IOUtils.toString(SPARQLGenerate.getStreamManager().open(new LookUpRequest(queryPath, SPARQLGenerate.MEDIA_TYPE)), "UTF-8");
        } catch (IOException | NullPointerException ex) {
            LOG.error(ex.getClass().getSimpleName() + " while loading main query " + queryPath);
            return;
        }
        SPARQLGenerateQuery q;
        try {
            q = (SPARQLGenerateQuery) QueryFactory.create(query, SPARQLGenerate.SYNTAX);
        } catch (Exception ex) {
            LOG.error("Error while parsing the query to be executed.", ex);
            return;
        }
        RootPlan plan;
        try {
            plan = PlanFactory.create(q);
        } catch (Exception ex) {
            LOG.error("Error while creating the plan for the query.", ex);
            return;
        }

        Dataset ds;
        try {
            ds = SPARQLGenerateUtils.loadDataset(dir, request);
        } catch (Exception ex) {
            LOG.warn("Error while loading the dataset, no dataset will be used.");
            ds = DatasetFactory.create();
        }

        if (request.stream) {
            StreamRDF outputStream = new WebSocketRDF(new PrintStream(new FileOutputStream(pwd + "/output.ttl", false)), EventServer.manager, q.getPrefixMapping());
            outputStream.start();
            plan.exec(ds, outputStream);
        } else {
            Model model = plan.exec(ds);
            StringWriter sw = new StringWriter();
            model.write(sw, "TTL");
            EventServer.manager.appendResponse(new Response("", sw.toString(), false));
            LOG.trace("end of transformation");
        }
    }

    private void stop(Exception ex, String msg, SessionManager sessionManager) {
        System.out.println(ex.getClass() + "occurred while " + msg + ": " + ex.getMessage());
        ex.printStackTrace();
        sessionManager.stop();
    }

    private static class WebSocketRDF implements StreamRDF {

        private final PrefixMapping pm;
        private final SerializationContext context;

        private final SessionManager session;

        private PrintStream out;
        int i = 0;

        public WebSocketRDF(PrintStream out, SessionManager session, PrefixMapping pm) {
            this.out = out;
            this.session = session;
            this.pm = pm;
            context = new SerializationContext(pm);
        }

        @Override
        public void start() {
            StringBuilder sb = new StringBuilder();
            pm.getNsPrefixMap().forEach((prefix, uri) -> {
                out.append("@prefix ").append(prefix).append(": <").append(uri).append("> .\n");
                sb.append("@prefix ").append(prefix).append(": <").append(uri).append("> .\n");
            });
            session.appendResponse(new Response("", sb.toString() + "\n", false));
        }

        @Override
        public void base(String string) {
            out.append("@base <").append(string).append(">\n");
            session.appendResponse(new Response("", "@base <" + string + ">\n", false));
        }

        @Override
        public void prefix(String prefix, String uri) {
            if (!uri.equals(pm.getNsPrefixURI(prefix))) {
                pm.setNsPrefix(prefix, uri);
                StringBuilder sb = new StringBuilder();
                sb.append("@prefix ").append(prefix).append(": <").append(uri).append("> .\n");
                out.append("@prefix ").append(prefix).append(": <").append(uri).append("> .\n");
                session.appendResponse(new Response("", sb.toString(), false));
            }
        }

        @Override
        public void triple(Triple triple) {
            Response response = new Response("", FmtUtils.stringForTriple(triple, context) + " .\n", false);
            session.appendResponse(response);

            out.append(FmtUtils.stringForTriple(triple, context)).append(" .\n");
            i++;
            if (i > 1000) {
                i = 0;
                out.flush();
            }
        }

        @Override
        public void quad(Quad quad) {
        }

        @Override
        public void finish() {
            out.flush();
            LOG.trace("end of transformation");
        }
    }

    static {
        com.jayway.jsonpath.Configuration.setDefaults(new com.jayway.jsonpath.Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider();
            private final MappingProvider mappingProvider
                    = new JacksonMappingProvider();

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }

}
