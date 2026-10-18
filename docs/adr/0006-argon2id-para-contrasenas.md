---
status: accepted
date: 2026-10-16
---

# Argon2id para almacenar contraseñas

## Contexto

Infracture incorpora el registro con correo y contraseña. No hay cuentas anteriores que requieran conservar otro formato de hash. La elección del codificador es independiente del transporte de sesión mediante JWT.

## Decisión

Usar directamente `Argon2PasswordEncoder` de Spring Security con Argon2id: 19 MiB de memoria, dos iteraciones, paralelismo uno, salt aleatorio de 16 bytes y hash de 32 bytes. No añadir selección de algoritmos ni compatibilidad con BCrypt.

Aceptar contraseñas de 15 a 128 puntos de código Unicode, normalizadas mediante NFC tanto en registro como en login. Conservar espacios y mayúsculas; permitir emojis. Exigir al menos una mayúscula, una minúscula, un dígito y un carácter de puntuación o símbolo. Rechazar caracteres de control. Rechazar contraseñas comunes mediante la lista local comprimida y coincidencias completas con datos de la cuenta.

## Motivo y consecuencias

OWASP recomienda Argon2id para aplicaciones nuevas. BCrypt introduce un límite de 72 bytes que complica las contraseñas Unicode largas. Mantener ambos algoritmos añadiría una migración que este proyecto no necesita.

NFC unifica representaciones Unicode equivalentes: por ejemplo, `é` como un único carácter y `e` seguida de un acento combinable. Sin normalización, el login podría rechazar la otra representación aunque el usuario vea la misma contraseña. Se aplica NFC antes de calcular el hash en el registro y antes de verificarlo en el login, siguiendo la recomendación de NIST para contraseñas Unicode. Conserva las tildes, las mayúsculas y los espacios; la longitud se comprueba después de normalizar.

Para consultar la lista de contraseñas bloqueadas y comparar con los datos de la cuenta, se prepara una copia con NFC, sin espacios exteriores y en minúsculas mediante `toLowerCase(Locale.ROOT)`. `Locale.ROOT` utiliza reglas neutrales respecto al idioma para que el resultado no dependa de la configuración regional del servidor. Así se detectan variantes de un valor bloqueado que solo cambian mayúsculas o espacios exteriores. Esta copia se usa únicamente para esas comprobaciones: la contraseña que recibe Argon2id conserva sus mayúsculas y espacios. Por ejemplo, `Mi café favorito 2026!` se compara como `mi café favorito 2026!`, pero se hashea con la `M` original.

La implementación requiere Bouncy Castle. La lista local permite validar sin Internet y conserva su procedencia y licencia; su contenido necesita actualización y no cubre todas las filtraciones posibles.

Las reglas de composición son restricciones de producto solicitadas por el alumno. OWASP y NIST recomiendan no exigir mezclas de tipos de caracteres; la política de composición del proyecto se aparta de esa recomendación.

La configuración está en [EncoderConfig](../../backend/src/main/java/es/codeurjc/infracture/security/EncoderConfig.java) y la política en [PasswordPolicy](../../backend/src/main/java/es/codeurjc/infracture/identity/application/PasswordPolicy.java). El controlador y el formulario de registro siguen pendientes.

## Referencias

- [OWASP: almacenamiento de contraseñas](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
- [NIST SP 800-63B-4: autenticación con contraseñas](https://pages.nist.gov/800-63-4/sp800-63b.html).
- [Java: normalización Unicode con Normalizer](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/text/Normalizer.html).
- [Java: Locale.ROOT](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Locale.html#ROOT).
