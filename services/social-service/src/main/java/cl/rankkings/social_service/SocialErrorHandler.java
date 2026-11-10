package cl.rankkings.social_service;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Map;
@RestControllerAdvice
public class SocialErrorHandler {
  @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<Map<String,Object>> status(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).body(Map.of("code","HTTP_ERROR","message",ex.getReason()==null?"Solicitud no válida":ex.getReason(),"status",ex.getStatusCode().value()));}
  @ExceptionHandler(MethodArgumentNotValidException.class) public ResponseEntity<Map<String,Object>> invalid(MethodArgumentNotValidException ex){return ResponseEntity.badRequest().body(Map.of("code","VALIDATION","message","Revisa los campos de la solicitud","status",400));}
  @ExceptionHandler(DataIntegrityViolationException.class) public ResponseEntity<Map<String,Object>> conflict(DataIntegrityViolationException ex){return ResponseEntity.status(409).body(Map.of("code","CONFLICT","message","Relación duplicada","status",409));}
}
