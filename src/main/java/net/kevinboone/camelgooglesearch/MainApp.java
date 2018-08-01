package net.kevinboone.camelgooglesearch;

import java.util.Properties; 
import java.io.File;
import java.io.FileInputStream;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.log4j.Logger;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import java.net.URLEncoder; 


/**
 * A Camel application that performs a search on a Google custom
 * search engine, parses the response, and forms an HTML file
 * with the results.
 *
 * Note that this program requires that the user's home directory contains
 * a file camelgooglesearch.props which defines the application's
 * customer search key reference ('cx') and application key.
 *
 * HTML files are written in the directory specified by outputDirectory,
 * and have the name "search_XXX.html", where XXX is the search term
 * specified on the command line.
 *
 * (c)2014 Kevin Boone
 */
public class MainApp 
  {
  // Define where we want the HTML files to be written
  static final String outputDirectory = "C:\\Users\\vibhu\\temp";

  static boolean done = false;

  /**
  main() -- execution starts here
  */
  public static void main(String[] args) throws Exception 
    {
    // Check we have been passed a search term on the command line
    if (args.length != 1)
      {
      System.err.println 
        ("Please specify a single search term on the command line.");
      System.exit (-1);
      }

    // Read the API key and search engine ref ('cx') from
    //  $HOME/camelgooglesearch.props
    String home = System.getProperty ("user.home");
    Properties props = new Properties ();
    props.load (new FileInputStream 
      (new File(home + File.separator + "camelgooglesearch.props")));
    final String key = (String)props.get ("key");
    final String cx = (String)props.get ("cx");
    final String searchTerm = args[0];

    // As always, create a CamelContext...
    CamelContext camelContext = new DefaultCamelContext();
  
    // ... and add the route definition to it
    camelContext.addRoutes (new RouteBuilder()
      { 
      public void configure() 
        {
        String outputFile = "search_" + searchTerm + ".html";

        from ("direct:start")
          // Force HTTP request to GET, because Google will reject a POST
          .setHeader (Exchange.HTTP_METHOD, constant("GET"))
          // Note that we must encode the searchTerm, as it will form
          //  part of a URL. So convert spaces the '+', etc. 
          .to ("https://www.googleapis.com/customsearch/v1"
            + "?q=" + URLEncoder.encode (searchTerm)
            + "&key=" + key 
            + "&cx=" + cx)
          // The message body now contains the response from the HTTP
          //  request, which is in JSON format (thanks, Google),
          //  so we convert it into a generic XML document...
          .unmarshal().xmljson()
          // And then split the XML into pieces matching <o><items><e></e>
          // Our Aggregator will be called once for each piece split
          //  from the XML
          .split (xpath("//o/items/e"), new Aggregator())
          // Note we need 2.13.0 for the following to work
          // Between here and the following end(), we are working on
          //  each chunk in the split XML file individually
          // Use XPath expressions to extract the bits from the XML
          //  that we are interested in, and put them in headers.
          // These will be used by the aggretator to build up the page
          .setHeader ("link", xpath ("//link", java.lang.String.class))
          .setHeader ("title", xpath ("//htmlTitle", java.lang.String.class))
          .setHeader ("snippet", xpath ("//htmlSnippet", 
             java.lang.String.class))
          // Set body to empty, because otherwise the aggregate will 
          //  begin with the original XML
          .setBody (constant(""))
          // The end() here marks the end of the split-aggregate operation.
          // Beyond this point, we are working on the aggretated result.
          .end()
          // At this point, the message body contains the aggregate
          //  page of search results. Replace the body ('enrich' is the
          //   trendy EIP term) by adding a simple title to the page
          .setBody (simple ("Search results for '<b>" + searchTerm 
             + "</b>':<p/> ${body}<p/>"))
           // Write the message body out to a file, whose name is
           //  based on the search term
          .to("file:" + outputDirectory + "?fileName=" + outputFile)
           // Finally, call setDone() to tell the program we are
           //  finished
          .process (new Processor ()
              {
              public void process (Exchange ex)
                {
                setDone(); 
                }
              }
            );
        }
      });

    // The route consumes from direct:start, not directly from the
    //  https: endpoint. This is important because, otherwise, 
    //  the route will issue HTTP requests repeatedly until the main
    //  thread realizes that it is being shut down. This way, we can
    //  be sure that the request will be issued only once, when a 
    //  message is posted to direct:start
    
    ProducerTemplate template = camelContext.createProducerTemplate();
    camelContext.start();

    template.sendBody ("direct:start", "");

    while (!done)
      {
      Thread.sleep (1000);
      }

    camelContext.stop();
    }

  /**
   * setDone sets the done flag so the program exits.
   */
  static void setDone()
    {
    done = true;
    }
  }

