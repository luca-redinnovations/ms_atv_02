package aula.microservicos.login;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import io.micrometer.core.instrument.*;
@Service
public class UsuarioService {
 private final JdbcTemplate jdbc;
 private final JdbcUserDetailsManager manager;
 private final Counter validos, invalidos;
 public UsuarioService(JdbcTemplate jdbc, JdbcUserDetailsManager manager, MeterRegistry registry) {
  this.jdbc=jdbc; this.manager=manager;
  validos=registry.counter("logins.validos"); invalidos=registry.counter("logins.invalidos");
  Gauge.builder("usuarios.bloqueados",jdbc,j->j.queryForObject("SELECT COUNT(*) FROM login_attempts a JOIN usuarios u ON u.username=a.username WHERE a.locked=TRUE",Integer.class)).register(registry);
 }
 @Transactional
 public boolean login(String username,String senha) {
  if(username==null || username.isBlank()) { invalidos.increment(); return false; }
  // A linha do usuário serializa login, desbloqueio e troca de senha concorrentes.
  var senhas=jdbc.queryForList("SELECT senha FROM usuarios WHERE username=? FOR UPDATE",String.class,username);
  if(senhas.isEmpty()) { invalidos.increment(); return false; }
  var locked=jdbc.queryForList("SELECT locked FROM login_attempts WHERE username=?",Boolean.class,username);
  if(!locked.isEmpty() && locked.get(0)) { invalidos.increment(); return false; }
  if(senha!=null && senha.equals(senhas.get(0))) { validos.increment(); return true; }
  jdbc.update("INSERT INTO login_attempts(username) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM login_attempts WHERE username=?)",username,username);
  jdbc.update("UPDATE login_attempts SET failed_attempts=failed_attempts+1,last_failed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE username=?",username);
  jdbc.update("UPDATE login_attempts SET locked=TRUE,locked_at=CURRENT_TIMESTAMP WHERE username=? AND failed_attempts>1",username);
  invalidos.increment(); return false;
 }
 @Transactional
 public void cadastrar(String username,String senha,String... roles) {
  if(manager.userExists(username)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Usuário já cadastrado");
  manager.createUser(User.withUsername(username).password("{noop}"+senha).roles(roles).build());
  jdbc.update("INSERT INTO usuarios(username,senha) VALUES (?,?)",username,senha);
 }
 @Transactional
 public void trocarSenha(String username,String atual,String nova) {
  var senhas=jdbc.queryForList("SELECT senha FROM usuarios WHERE username=? FOR UPDATE",String.class,username);
  if(senhas.isEmpty() || !java.util.Objects.equals(senhas.get(0),atual)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Usuário ou senha atual inválidos");
  jdbc.update("UPDATE usuarios SET senha=? WHERE username=?",nova,username);
  jdbc.update("UPDATE users SET password=? WHERE username=?","{noop}"+nova,username);
 }
 @Transactional
 public void desbloquear(String username) {
  if(jdbc.queryForList("SELECT username FROM usuarios WHERE username=? FOR UPDATE",String.class,username).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuário inexistente");
  jdbc.update("UPDATE login_attempts SET failed_attempts=0,locked=FALSE,last_failed_at=NULL,locked_at=NULL,updated_at=CURRENT_TIMESTAMP WHERE username=?",username);
 }
 public List<Map<String,Object>> listar() { return jdbc.query("SELECT username FROM usuarios ORDER BY username",(rs,n)->Map.<String,Object>of("username",rs.getString("username"))); }
}
