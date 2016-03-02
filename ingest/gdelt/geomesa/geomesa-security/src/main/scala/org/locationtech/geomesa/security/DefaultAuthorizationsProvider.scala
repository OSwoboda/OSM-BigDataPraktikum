/***********************************************************************
* Copyright (c) 2013-2016 Commonwealth Computer Research, Inc.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Apache License, Version 2.0
* which accompanies this distribution and is available at
* http://www.opensource.org/licenses/apache2.0.php.
*************************************************************************/

package org.locationtech.geomesa.security

import org.apache.accumulo.core.security.Authorizations

/**
 * Default implementation of the AuthorizationsProvider that doesn't provide any authorizations
 */
class DefaultAuthorizationsProvider extends AuthorizationsProvider {

  var authorizations: Authorizations = new Authorizations

  override def getAuthorizations: Authorizations = authorizations

  override def configure(params: java.util.Map[String, java.io.Serializable]) {
    val authString = authsParam.lookUp(params).asInstanceOf[String]
    if (authString == null || authString.isEmpty)
      authorizations = new Authorizations()
    else
      authorizations = new Authorizations(authString.split(","):_*)
  }

}
