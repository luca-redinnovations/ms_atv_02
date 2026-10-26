package aula.microservicos.login;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
@Configuration
public class BootstrapConfig {
 @Bean ApplicationRunner bootstrap(UsuarioService service,JdbcUserDetailsManager manager,
  @Value("${bootstrap.username}") String username,@Value("${bootstrap.senha}") String senha) {
  return args->{
   if(username.isEmpty() && senha.isEmpty()) return;
   if(username.isBlank() || username.length()>100 || senha.isBlank() || senha.length()>10)
    throw new IllegalArgumentException("Informe BOOTSTRAP_USERNAME (até 100 caracteres) e BOOTSTRAP_SENHA (até 10 caracteres)");
   if(!manager.userExists(username)) service.cadastrar(username,senha,"MANUTENCAO","ADMIN");
  };
 }
}
