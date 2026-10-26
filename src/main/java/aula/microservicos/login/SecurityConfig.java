package aula.microservicos.login;
import javax.sql.DataSource;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import static org.springframework.security.config.Customizer.withDefaults;
@Configuration
@SecurityScheme(name="basicAuth",type=SecuritySchemeType.HTTP,scheme="basic")
public class SecurityConfig {
 @Bean JdbcUserDetailsManager usuarios(DataSource dataSource) {
  var manager = new JdbcUserDetailsManager(dataSource);
  // A senha e o bloqueio são consultados nas tabelas exigidas pelo exercício.
  manager.setUsersByUsernameQuery("SELECT u.username, CONCAT('{noop}',u.senha), CASE WHEN COALESCE(a.locked,FALSE) THEN FALSE ELSE TRUE END FROM usuarios u LEFT JOIN login_attempts a ON a.username=u.username WHERE u.username=?");
  return manager;
 }
 @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
 @Bean SecurityFilterChain seguranca(HttpSecurity http) throws Exception {
  return http.csrf(csrf->csrf.disable())
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .httpBasic(withDefaults())
   .authorizeHttpRequests(r->r
    .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
    .requestMatchers(HttpMethod.POST,"/login").permitAll()
    .requestMatchers(HttpMethod.PUT,"/trocarsenha/*").permitAll()
    .requestMatchers("/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/actuator/health","/actuator/prometheus").permitAll()
    .requestMatchers(HttpMethod.POST,"/").hasRole("MANUTENCAO")
    .requestMatchers(HttpMethod.GET,"/").hasRole("MANUTENCAO")
    .requestMatchers(HttpMethod.PUT,"/unlock/*").hasRole("ADMIN")
    .anyRequest().denyAll()).build();
 }
}
