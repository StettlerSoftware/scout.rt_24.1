/*
 * Copyright (c) BSI Business Systems Integration AG. All rights reserved.
 * http://www.bsiag.com/
 */
package org.eclipse.scout.rt.rest.exception;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.eclipse.scout.rt.platform.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Bean
public class WebApplicationExceptionMapper extends AbstractExceptionMapper<WebApplicationException> {
  private static final Logger LOG = LoggerFactory.getLogger(WebApplicationExceptionMapper.class);

  @Override
  public Response toResponseImpl(WebApplicationException exception) {
    LOG.debug("{}: {}", exception.getClass().getSimpleName(), exception.getMessage());
    return createResponse(exception);
  }

  protected Response createResponse(WebApplicationException exception) {
    return exception.getResponse();
  }
}
