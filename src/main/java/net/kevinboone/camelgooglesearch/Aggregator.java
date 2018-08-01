package net.kevinboone.camelgooglesearch;

import org.apache.camel.processor.aggregate.AggregationStrategy; 
import org.apache.camel.Exchange;

/**
 * Aggregator is called by the splitter component, every time a
 * new search result item is split out of the incoming XML file.
 * It takes the link, title, and HTML snippet passed in the message
 * headers, and builds a fragment of HTML. Each fragment
 * is appended to the complete message, eventually to form a 
 * page of search results.
 *
 * (c)2014 Kevin Boone
 */
class Aggregator implements AggregationStrategy 
  {
  public Exchange aggregate (Exchange oldEx, Exchange newEx)
    {
    if (oldEx == null) return newEx;

    // Get the relevant data from the message header. Note that 
    //  the message body contains the data that is being built up
    //  by the aggregation process
    String link = (String) newEx.getIn().getHeader ("link");
    String title = (String) newEx.getIn().getHeader ("title");
    String snippet = (String) newEx.getIn().getHeader ("snippet");

    // Get the existing message body... 
    String existing = oldEx.getIn().getBody (String.class);

    // ... and append to it the new search result ...
    String newBody = existing + 
        "<a href=\"" + link + "\">" + title + "</a>" + "<br/>" + 
        snippet + "<p/>\n";

    // ... then replace the old message body with the extended version
    //   containing the new formatted search result
    oldEx.getIn().setBody (newBody);
    
    return oldEx;
    }
  }


