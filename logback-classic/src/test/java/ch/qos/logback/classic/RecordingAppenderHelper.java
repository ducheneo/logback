package ch.qos.logback.classic;

import org.slf4j.Logger;

/**
 * @author Tomasz Nurkiewicz
 * @since 14.04.11, 22:47
 */
public class RecordingAppenderHelper {


  private final Logger log;

  public RecordingAppenderHelper(Logger log) {
    this.log = log;
  }

  public void otherMethod(String msg) {
    log.info(msg);
  }
}
