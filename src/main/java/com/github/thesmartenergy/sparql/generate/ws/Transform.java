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
import java.util.Vector;
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
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.AnonId;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

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
//      BufferedReader bodyReader = request.getReader();
        List<String> queries = new Vector<String>();
        List<String> queryUrls = new Vector<String>();
        List<String> documents = new Vector<String>();
        List<JSONObject> bindings = new Vector<JSONObject>();

        try {
          JSONObject bodyJSON = new JSONObject(new JSONTokener(request.getReader()));
          try {
            for (Object queryStr : bodyJSON.getJSONArray("queries")) {
                queries.add((String) queryStr);
            }
          } catch(JSONException e) {
          }
          try {
            for (Object queryUrlStr : bodyJSON.getJSONArray("queryurls")) {
                queryUrls.add((String) queryUrlStr);
            }
          } catch(JSONException e) {
          }
          try {
            for (Object bindingsJSON : bodyJSON.getJSONArray("bindings")) {
                bindings.add((JSONObject) bindingsJSON);
            }
          } catch(JSONException e) {
          }

          // String[] queries, String[] queryurls,
          // String[] documents, String[] bindings,

        } catch(JSONException e) {
        }

        String[] queryParams = request.getParameterValues("query");
        String[] queryUrlParams = request.getParameterValues("queryurl");
        String[] documentParams = request.getParameterValues("document");
        String[] bindingsParams = request.getParameterValues("bindings");

        if (queryParams != null) {
          for (String queryParam: queryParams) {
            queries.add(queryParam);
          }
        }
        if (queryUrlParams != null) {
          for (String queryUrlParam: queryUrlParams) {
            queryUrls.add(queryUrlParam);
          }
        }
        if (documentParams != null) {
          for (String documentParam: documentParams) {
            documents.add(documentParam);
          }
        }
        if (bindingsParams != null) {
          for (String bindingsStr: bindingsParams) {
            bindings.add(new JSONObject(bindingsStr));
          }
        }

        doTransform(
                queries, //.toArray(new String[0]),
                queryUrls, //.toArray(new String[0]),
                documents, //.toArray(new String[0]),
                bindings, //.toArray(new JSONObject[0]),
                response);
    }

    private void execQuery(Model model, String query, QuerySolution initialBindings) {
      RootPlan plan = PlanFactory.create(query);
      plan.exec(EMPTY_MODEL, initialBindings, model);
    }

    private RDFNode jsonToRDFNode(Object object, Model model) {
      RDFNode result = null;
      if (object instanceof JSONObject) {
        JSONObject json = (JSONObject) object;

        String type = json.getString("type");
        String value = json.getString("value");

        if (type.equals("uri")) {
          result = model.createResource(value);
        } else if (type.equals("literal")) {
          try {
            String datatype = json.getString("datatype");
            result = model.createTypedLiteral(value, NodeFactory.getType(datatype));
          } catch(JSONException e) {
            try {
              String lang = json.getString("xml:lang");
              result = model.createLiteral(value, lang);
            } catch(JSONException e2) {
              result = model.createLiteral(value, false);
            }
          }
        } else if (type.equals("bnode")) {
          if (value != null) {
            result = model.createResource(AnonId.create(value));
          } else {
            result = model.createResource();
          }
        }

      } else {
      	result = model.createTypedLiteral(object);
      }

      return result;
    }

    private void doTransform(
        Iterable<String> queries, Iterable<String> queryurls,
        Iterable<String> documents, Iterable<JSONObject> bindings,
        // String[] queries, String[] queryurls,
        // String[] documents, JSONObject[] bindings,
        HttpServletResponse response) throws IOException {

      System.out.println("Queries: " + ((queries == null) ? "[]" : String.join(", ", queries)) + ".");
      System.out.println("Queryurls: " + ((queryurls == null) ? "[]" : String.join(", ", queryurls)) + ".");
      System.out.println("Documents: " + ((documents == null) ? "[]" : String.join(", ", documents)) + ".");
      System.out.println("Bindings: " + ((bindings == null) ? "[]" : String.join(", ", bindings.toString())) + ".");

      final ExecutorService service = Executors.newSingleThreadExecutor();
      Model model = ModelFactory.createDefaultModel();
      List<QuerySolutionMap> initialBindings = new LinkedList<QuerySolutionMap>();

      if (bindings != null) {
        for (JSONObject bindingsJson: bindings) {
          QuerySolutionMap currBindings = new QuerySolutionMap();
          initialBindings.add(currBindings);
          for (String key: bindingsJson.keySet()) {
            currBindings.add(key, jsonToRDFNode(bindingsJson.get(key), model));
          }
        }
      }
      if (initialBindings.isEmpty()) {
        initialBindings.add(new QuerySolutionMap());
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
          for (QuerySolution currBindings: initialBindings) {
            execQuery(model, query, currBindings);
          }
        }
      }
      if (queryurls != null) {
        for (String queryurl: queryurls) {
          for (QuerySolution currBindings: initialBindings) {
            URL url = new URL(queryurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            String query = IOUtils.toString(conn.getInputStream());
            execQuery(model, query, currBindings);
          }
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
