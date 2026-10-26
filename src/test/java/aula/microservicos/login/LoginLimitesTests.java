package aula.microservicos.login;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={
 "spring.datasource.url=jdbc:h2:mem:limites;DB_CLOSE_DELAY=-1",
 "bootstrap.username=", "bootstrap.senha="
})
class LoginLimitesTests {
 @Autowired UsuarioService service;
 @Autowired JdbcTemplate jdbc;

 @Test void duasSenhasVaziasTambemBloqueiam() {
  service.cadastrar("vazia","correta");
  assertFalse(service.login("vazia",""));
  assertFalse(service.login("vazia",""));
  assertTrue(jdbc.queryForObject("SELECT locked FROM login_attempts WHERE username='vazia'",Boolean.class));
  assertFalse(service.login("vazia","correta"));
 }

 @Test void usernameNaoPodeAlterarConsultaSql() {
  service.cadastrar("sql","correta");
  assertFalse(service.login("' OR '1'='1","correta"));
  assertTrue(service.login("sql","correta"));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE username='sql'",Integer.class));
 }

 @Test void duasFalhasSimultaneasNaoPerdemTentativas() throws Exception {
  service.cadastrar("concorrente","correta");
  var executor=Executors.newFixedThreadPool(2);
  var iniciar=new CountDownLatch(1);
  try {
   Callable<Boolean> falhar=()->{iniciar.await();return service.login("concorrente","errada");};
   var primeira=executor.submit(falhar);
   var segunda=executor.submit(falhar);
   iniciar.countDown();
   assertFalse(primeira.get(10,TimeUnit.SECONDS));
   assertFalse(segunda.get(10,TimeUnit.SECONDS));
   assertEquals(2,jdbc.queryForObject("SELECT failed_attempts FROM login_attempts WHERE username='concorrente'",Integer.class));
   assertTrue(jdbc.queryForObject("SELECT locked FROM login_attempts WHERE username='concorrente'",Boolean.class));
   assertFalse(service.login("concorrente","correta"));
  } finally { executor.shutdownNow(); }
 }
}
