package ch.qos.logback.classic.db.mongo;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.db.mongo.MongoDBAppenderBase;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.Date;

/**
 * @author Tomasz Nurkiewicz
 * @since 02.04.11, 15:01
 */
public class MongoDBAppender extends MongoDBAppenderBase<ILoggingEvent> {

  private boolean includeCallerData;

  public MongoDBAppender() {
    super("loggingEvents");
  }

  @Override
  protected BasicDBObject toMongoDocument(ILoggingEvent event) {
    final BasicDBObject doc = new BasicDBObject();
    doc.append("timeStamp", new Date(event.getTimeStamp()));
    doc.append("level", event.getLevel().levelStr);
    doc.append("thread", event.getThreadName());
    if (event.getMdc() != null && !event.getMdc().isEmpty())
      doc.append("mdc", event.getMdc());
    doc.append("logger", event.getLoggerName());
    if (includeCallerData)
      doc.append("callerData", toDocument(event.getCallerData()));
    doc.append("message", event.getFormattedMessage());
    if (event.getArgumentArray() != null && event.getArgumentArray().length > 0)
      doc.append("arguments", event.getArgumentArray());
    appendThrowableIfAvailable(doc, event);
    return doc;
  }

  private BasicDBList toDocument(StackTraceElement[] callerData) {
    final BasicDBList dbList = new BasicDBList();
    for (final StackTraceElement ste : callerData) {
      dbList.add(
          new BasicDBObject()
              .append("file", ste.getFileName())
              .append("class", ste.getClassName())
              .append("method", ste.getMethodName())
              .append("line", ste.getLineNumber())
              .append("native", ste.isNativeMethod()));
    }
    return dbList;
  }

  private void appendThrowableIfAvailable(BasicDBObject doc, ILoggingEvent event) {
    if (event.getThrowableProxy() != null) {
      final BasicDBObject val = toMongoDocument(event.getThrowableProxy());
      doc.append("throwable", val);
    }
  }

  private BasicDBObject toMongoDocument(IThrowableProxy throwable) {
    final BasicDBObject throwableDoc = new BasicDBObject();
    throwableDoc.append("class", throwable.getClassName());
    throwableDoc.append("message", throwable.getMessage());
    throwableDoc.append("stackTrace", toSteArray(throwable));
    if (throwable.getCause() != null)
      throwableDoc.append("cause", toMongoDocument(throwable.getCause()));
    return throwableDoc;
  }

  private String[] toSteArray(IThrowableProxy throwableProxy) {
    final StackTraceElementProxy[] elementProxies = throwableProxy.getStackTraceElementProxyArray();
    final int totalFrames = elementProxies.length - throwableProxy.getCommonFrames();
    final String[] stackTraceElements = new String[totalFrames];
    for (int i = 0; i < totalFrames; ++i)
      stackTraceElements[i] = elementProxies[i].getStackTraceElement().toString();
    return stackTraceElements;
  }

  public void setIncludeCallerData(boolean includeCallerData) {
    this.includeCallerData = includeCallerData;
  }
}
