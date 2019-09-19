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

import com.github.thesmartenergy.sparql.generate.jena.engine.PlanFactory;
import com.github.thesmartenergy.sparql.generate.jena.engine.RootPlan;
import com.github.thesmartenergy.sparql.generate.jena.stream.LocatorStringMap;
import com.github.thesmartenergy.sparql.generate.jena.stream.SPARQLGenerateStreamManager;
import com.github.thesmartenergy.sparql.generate.jena.SPARQLGenerate;
import java.io.IOException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.json.JSONException;
import org.json.JSONObject;

/**
 *
 * @author Miguel Ceriani <miguel.ceriani at gmail.com>
 */
public class Transform extends HttpServlet {

    private static final Model EMPTY_MODEL = ModelFactory.createDefaultModel();

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGetOrPost(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGetOrPost(request, response);
    }

    protected void doGetOrPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doTransform(
                request.getParameterValues("query"),
                request.getParameterValues("queryurl"),
                request.getParameterValues("document"),
                request.getParameterValues("bindings"),
                response);
    }

    private void execQuery(Model model, String query, QuerySolution initialBindings) {
      RootPlan plan = PlanFactory.create(query);
      plan.exec(EMPTY_MODEL, initialBindings, model);
    }

    private RDFNode jsonToRDFNode(JSONObject json) {
      // if (json instanceof JSONObject) {
      RDFNode result = null;
      String type = json.getString("type");
      String value = json.getString("value");

      // RDF Term	JSON form
      // IRI I	{"type": "uri", "value": "I"}
      // Literal S	{"type": "literal","value": "S"}
      // Literal S with language tag L	{ "type": "literal", "value": "S", "xml:lang": "L"}
      // Literal S with datatype IRI D	{ "type": "literal", "value": "S", "datatype": "D"}
      // Blank node, label B	{"type": "bnode", "value": "B"}
      if (type.equals("uri")) {
      } else if (type.equals("literal")) {
        String datatype = json.getString("datatype");
        String lang = json.getString("xml:lang");
        if (datatype != null) {
          // result = NodeFactory.createLiteral(value, lang, RDFDatatype dtype);
        }
      } else if (type.equals("bnode")) {

      }
      return result;
      // } else (json instanceof String)
    }

    private void doTransform(
        String[] queries, String[] queryurls,
        String[] documents, String[] bindings,
        HttpServletResponse response) throws IOException {
      final ExecutorService service = Executors.newSingleThreadExecutor();
      Model model = ModelFactory.createDefaultModel();
      List<QuerySolutionMap> initialBindings = new LinkedList<QuerySolutionMap>();

      if (bindings != null) {
        for (String bindingsStr: bindings) {
          QuerySolutionMap currBindings = new QuerySolutionMap();
          initialBindings.add(currBindings);
          JSONObject bindingsJson = new JSONObject(bindingsStr);
          for (key: bindingsJson.keySet()) {
            // currBindings.add(key, jsonToRDFNode(bindingsJson.get(key)));
            currBindings.add(key, jsonToRDFNode(bindingsJson.getJSONObject(key)));
          }
        }
      }

      if (documents != null) {
        LocatorStringMap locator = new LocatorStringMap();
        for (String documentStr: documents) {
          try {
            JSONObject document = new JSONObject(documentStr);
            locator.put(
                document.getString("name"),
                document.getString("text"),
                document.getString("mediaType"));
          } catch(JSONException e) {
            e.printStackTrace();
          }
        }

        // initialize the StreamManager
        SPARQLGenerateStreamManager sm = SPARQLGenerateStreamManager.makeStreamManager(locator);
        SPARQLGenerate.setStreamManager(sm);

      }

      if (queries != null) {
        for (String query: queries) {
          execQuery(model, query, initialBindings);
        }
      }
      if (queryurls != null) {
        for (String queryurl: queryurls) {
          URL url = new URL(queryurl);
          HttpURLConnection conn = (HttpURLConnection) url.openConnection();
          conn.setRequestMethod("GET");
          String query = IOUtils.toString(conn.getInputStream());
          execQuery(model, query, initialBindings);
        }
      }

      response.setStatus(HttpServletResponse.SC_OK);
      response.setCharacterEncoding("UTF-8");
      response.setContentType("text/turtle");
      Writer responseWriter = response.getWriter();
      model.write(responseWriter, "TTL", "http://example.org/");
      responseWriter.close();
    }

}
