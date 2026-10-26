package aula.microservicos.login;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=jdbc:h2:mem:teste;DB_CLOSE_DELAY=-1",
 "bootstrap.username=gestor", "bootstrap.senha=teste123"
})
class Atividade2Tests {
 @Autowired Environment env;
 @Autowired JdbcTemplate jdbc;
 @Autowired UsuarioService service;
 private final HttpClient client=HttpClient.newHttpClient();
 private HttpResponse<String> request(String method,String path,String body,String auth) throws Exception {
  var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+env.getProperty("local.server.port")+path));
  if(auth!=null) builder.header("Authorization","Basic "+Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8)));
  if(body!=null) builder.header("Content-Type","application/json");
  return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 @Test void requisitosDoEnunciado() throws Exception {
  service.cadastrar("somenteadmin","teste123","ADMIN");
  service.cadastrar("somentemanut","teste123","MANUTENCAO");
  assertEquals(401,request("GET","/",null,null).statusCode());
  assertEquals(403,request("GET","/",null,"somenteadmin:teste123").statusCode());
  assertEquals(403,request("POST","/","{\"username\":\"negado\",\"senha\":\"abc\"}","somenteadmin:teste123").statusCode());
  assertEquals(200,request("POST","/","{\"username\":\"aluno\",\"senha\":\"abc\"}","somentemanut:teste123").statusCode());
  assertEquals(200,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"abc\"}",null).statusCode());
  assertEquals(401,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"erro\"}",null).statusCode());
  assertFalse(jdbc.queryForObject("SELECT locked FROM login_attempts WHERE username='aluno'",Boolean.class));
  assertEquals(401,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"erro\"}",null).statusCode());
  assertTrue(jdbc.queryForObject("SELECT locked FROM login_attempts WHERE username='aluno'",Boolean.class));
  assertEquals(2,jdbc.queryForObject("SELECT failed_attempts FROM login_attempts WHERE username='aluno'",Integer.class));
  assertEquals(401,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"abc\"}",null).statusCode());
  assertEquals(403,request("PUT","/unlock/aluno",null,"somentemanut:teste123").statusCode());
  assertEquals(200,request("PUT","/unlock/aluno",null,"somenteadmin:teste123").statusCode());
  assertFalse(jdbc.queryForObject("SELECT locked FROM login_attempts WHERE username='aluno'",Boolean.class));
  assertEquals(401,request("PUT","/trocarsenha/aluno","{\"atual\":\"erro\",\"nova\":\"nova\"}",null).statusCode());
  assertEquals(200,request("PUT","/trocarsenha/aluno","{\"atual\":\"abc\",\"nova\":\"nova\"}",null).statusCode());
  assertEquals(200,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"nova\"}",null).statusCode());
  assertEquals(401,request("GET","/",null,"aluno:nova").statusCode());
  var list=request("GET","/",null,"somentemanut:teste123");
  assertEquals(200,list.statusCode()); assertTrue(list.body().contains("aluno")); assertFalse(list.body().contains("senha"));
  assertEquals(409,request("POST","/","{\"username\":\"aluno\",\"senha\":\"nova\"}","somentemanut:teste123").statusCode());
  assertEquals(200,request("GET","/v3/api-docs",null,null).statusCode());
  assertEquals(200,request("GET","/swagger-ui/index.html",null,null).statusCode());
  String metrics=request("GET","/actuator/prometheus",null,null).body();
  assertTrue(metrics.contains("logins_validos_total 2.0"));
  assertTrue(metrics.contains("logins_invalidos_total 3.0"));
  assertTrue(metrics.contains("usuarios_bloqueados 0.0"));
  // O bloqueio também impede autenticação HTTP Basic nos endpoints protegidos.
  service.login("somentemanut","erro"); service.login("somentemanut","erro");
  assertEquals(401,request("GET","/",null,"somentemanut:teste123").statusCode());
  assertTrue(request("GET","/actuator/prometheus",null,null).body().contains("usuarios_bloqueados 1.0"));
  // Nenhum endpoint protegido aceita chamadas anônimas.
  assertEquals(401,request("POST","/","{\"username\":\"outro\",\"senha\":\"abc\"}",null).statusCode());
  assertEquals(401,request("PUT","/unlock/aluno",null,null).statusCode());
  // Credenciais inválidas têm o retorno 401 definido no enunciado.
  assertEquals(401,request("POST","/login","{\"username\":\"\",\"senha\":\"\"}",null).statusCode());
  assertEquals(401,request("POST","/login","{}",null).statusCode());
  assertEquals(401,request("POST","/login","{\"username\":\"inexistente\",\"senha\":\"abc\"}",null).statusCode());
  assertEquals(401,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"maiorque10caracteres\"}",null).statusCode());
  assertEquals(401,request("PUT","/trocarsenha/inexistente","{\"atual\":\"abc\",\"nova\":\"nova\"}",null).statusCode());
  assertEquals(404,request("PUT","/unlock/inexistente",null,"somenteadmin:teste123").statusCode());
  assertEquals(400,request("POST","/","{\"username\":\"limite\",\"senha\":\"12345678901\"}","gestor:teste123").statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE username='limite'",Integer.class));
  // A senha anterior deixa de funcionar depois da troca.
  assertEquals(401,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"abc\"}",null).statusCode());
  assertEquals(401,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"nova\"}",null).statusCode());
  assertEquals(200,request("PUT","/unlock/aluno",null,"somenteadmin:teste123").statusCode());
  assertEquals(200,request("POST","/login","{\"username\":\"aluno\",\"senha\":\"nova\"}",null).statusCode());
  assertEquals(0,jdbc.queryForObject("SELECT failed_attempts FROM login_attempts WHERE username='aluno'",Integer.class));
  assertTrue(jdbc.queryForObject("SELECT locked_at IS NULL FROM login_attempts WHERE username='aluno'",Boolean.class));
  // As cinco rotas constam no documento OpenAPI gerado.
  String openapi=request("GET","/v3/api-docs",null,null).body();
  for(String path:new String[]{"/login","/trocarsenha/{userid}","/","/unlock/{userid}"}) assertTrue(openapi.contains("\""+path+"\""));

 }
}
