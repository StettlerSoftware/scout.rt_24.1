#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )
package ${package}.shared.helloworld;

import org.eclipse.scout.rt.platform.service.IService;
import org.eclipse.scout.rt.shared.TunnelToServer;

import ${package}.shared.helloworld.HelloWorldFormData;

/**
 * <h3>{@link IHelloWorldFormService}</h3>
 *
 * @author ${userName}
 */
@TunnelToServer
public interface IHelloWorldFormService extends IService {
      HelloWorldFormData load(HelloWorldFormData input);
}
