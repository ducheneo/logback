package ch.qos.logback.access.db.mongo;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.core.db.mongo.MongoDBAppenderBase;
import com.mongodb.BasicDBObject;

import java.util.Date;

/**
 * @author Tomasz Nurkiewicz
 * @since 03.04.11, 00:00
 */
public class MongoDBAppender extends MongoDBAppenderBase<IAccessEvent> {

  public MongoDBAppender() {
    super("accessEvents");
  }

  private boolean server = true;
  private boolean requestUri = true;
  private boolean requestProtocol = true;
  private boolean requestMethod = true;
  private boolean requestPostContent = true;
  private boolean requestSessionId = true;
  private boolean requestUserAgent = true;
  private boolean requestReferer = true;
  private boolean remoteHost = true;
  private boolean remoteUser = true;
  private boolean remoteAddr = true;
  private boolean responseContentLength = true;
  private boolean responseStatusCode = true;

  @Override
  protected BasicDBObject toMongoDocument(IAccessEvent event) {
    final BasicDBObject doc = new BasicDBObject();
    doc.append("timeStamp", new Date(event.getTimeStamp()));
    if(server)
      doc.append("server", event.getServerName());
    addRemote(doc, event);
    addRequest(doc, event);
    addResponse(doc, event);
    return doc;
  }

  private void addResponse(BasicDBObject doc, IAccessEvent event) {
    final BasicDBObject response = new BasicDBObject();
    if (responseContentLength)
      response.append("contentLength", event.getContentLength());
    if (responseStatusCode)
      response.append("statusCode", event.getStatusCode());
    if (!response.isEmpty())
      doc.append("response", response);
  }

  private void addRequest(BasicDBObject parent, IAccessEvent event) {
    final BasicDBObject request = new BasicDBObject();
    final String uri = event.getRequestURI();
    if (requestUri && uri != null && !uri.equals("-")) {
      request.append("uri", uri);
    }
    if (requestProtocol)
      request.append("protocol", event.getProtocol());
    if (requestMethod)
      request.append("method", event.getMethod());
    final String requestContent = event.getRequestContent();
    if (requestPostContent && requestContent != null && !requestContent.equals("")) {
      request.append("postContent", requestContent);
    }
    final String jSessionId = event.getCookie("JSESSIONID");
    if (requestSessionId && !jSessionId.equals("-"))
      request.append("sessionId", jSessionId);
    final String userAgent = event.getRequestHeader("User-Agent");
    if (requestUserAgent && !userAgent.equals("-"))
      request.append("userAgent", userAgent);
    final String referer = event.getRequestHeader("Referer");
    if (requestReferer && !referer.equals("-"))
      request.append("referer", referer);
    if (!request.isEmpty())
      parent.put("request", request);
  }

  private void addRemote(BasicDBObject parent, IAccessEvent event) {
    final BasicDBObject remote = new BasicDBObject();
    if (remoteHost)
      remote.append("host", event.getRemoteHost());
    final String remoteUserName = event.getRemoteUser();
    if (remoteUser && remoteUserName != null && !remoteUserName.equals("-")) {
      remote.append("user", remoteUserName);
    }
    if (remoteAddr)
      remote.append("addr", event.getRemoteAddr());
    if (!remote.isEmpty())
      parent.put("remote", remote);
  }

  public void setServer(boolean server) {
    this.server = server;
  }

  public void setRequestUri(boolean requestUri) {
    this.requestUri = requestUri;
  }

  public void setRequestProtocol(boolean requestProtocol) {
    this.requestProtocol = requestProtocol;
  }

  public void setRequestMethod(boolean requestMethod) {
    this.requestMethod = requestMethod;
  }

  public void setRequestPostContent(boolean requestPostContent) {
    this.requestPostContent = requestPostContent;
  }

  public void setRequestSessionId(boolean requestSessionId) {
    this.requestSessionId = requestSessionId;
  }

  public void setRequestUserAgent(boolean requestUserAgent) {
    this.requestUserAgent = requestUserAgent;
  }

  public void setRequestReferer(boolean requestReferer) {
    this.requestReferer = requestReferer;
  }

  public void setRemoteHost(boolean remoteHost) {
    this.remoteHost = remoteHost;
  }

  public void setRemoteUser(boolean remoteUser) {
    this.remoteUser = remoteUser;
  }

  public void setRemoteAddr(boolean remoteAddr) {
    this.remoteAddr = remoteAddr;
  }

  public void setResponseContentLength(boolean responseContentLength) {
    this.responseContentLength = responseContentLength;
  }

  public void setResponseStatusCode(boolean responseStatusCode) {
    this.responseStatusCode = responseStatusCode;
  }
}
