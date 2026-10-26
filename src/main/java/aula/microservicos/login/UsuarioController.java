package aula.microservicos.login;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
@RestController
public class UsuarioController {
 private final UsuarioService service;
 public UsuarioController(UsuarioService service) { this.service=service; }
 public record Credenciais(@NotBlank @Size(max=100) String username,@NotBlank @Size(max=10) String senha) {}
 public record Senhas(@NotBlank @Size(max=10) String atual,@NotBlank @Size(max=10) String nova) {}
 @PostMapping("/login")
 @Operation(summary="Validar login",responses={@ApiResponse(responseCode="200",description="Usuário e senha válidos"),@ApiResponse(responseCode="401",description="Credenciais inválidas ou usuário bloqueado")})
 public ResponseEntity<Void> login(@RequestBody Credenciais c) { return ResponseEntity.status(service.login(c.username(),c.senha())?200:401).build(); }
 @PutMapping("/trocarsenha/{userid}")
 @Operation(summary="Trocar senha mediante confirmação da senha atual",responses={@ApiResponse(responseCode="200",description="Senha alterada"),@ApiResponse(responseCode="401",description="Usuário ou senha atual inválidos")})
 public void trocar(@PathVariable String userid,@Valid @RequestBody Senhas s) { service.trocarSenha(userid,s.atual(),s.nova()); }
 @PostMapping("/")
 @Operation(summary="Cadastrar usuário (ROLE_MANUTENCAO)",security=@SecurityRequirement(name="basicAuth"))
 public void cadastrar(@Valid @RequestBody Credenciais c) { service.cadastrar(c.username(),c.senha()); }
 @PutMapping("/unlock/{userid}")
 @Operation(summary="Desbloquear usuário (ROLE_ADMIN)",security=@SecurityRequirement(name="basicAuth"))
 public void desbloquear(@PathVariable String userid) { service.desbloquear(userid); }
 @GetMapping("/")
 @Operation(summary="Listar usuários (ROLE_MANUTENCAO)",security=@SecurityRequirement(name="basicAuth"))
 public List<Map<String,Object>> listar() { return service.listar(); }
}
