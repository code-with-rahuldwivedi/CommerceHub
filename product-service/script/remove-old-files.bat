#!/usr/bin/env bash
# Run from the project root (the folder that contains pom.xml):  bash scripts/remove-old-files.sh
# Deletes the files of the OLD product-service that must not coexist with the new code.
set -euo pipefail

B=src/main/java/com/shopsphere/productservice
OLD=(
  "$B/security/JwtAuthenticationFilter.java"   # used jjwt (removed from pom) and the old HS256 scheme
  "$B/security/JwtService.java"                # same
  "$B/service/ProductServiceImpl.java"         # implements the old ProductService interface, uses old DTO getters
  "src/main/resources/application.properties"  # replaced by application.yml
  "src/test/resources/application.properties"  # replaced by application-test.yml / application-h2.yml
)

for f in "${OLD[@]}"; do
  if [ -f "$f" ]; then rm -v "$f"; else echo "already gone: $f"; fi
done

echo
echo "Java files now in the project that are NOT part of the new layout (should be none):"
EXPECTED="ProductServiceApplication SecurityConfig SecurityJwtProperties AppProperties ProductController LegacyProductController ProductQuery ProductRequest ProductResponse StatusRequest Category Product ProductStatus ConflictException GlobalExceptionHandler InvalidRequestException ProductNotFoundException ProductRepository ProductSpecifications CorrelationIdFilter CurrentUser JwtDecoderFactory ProblemDetailWriter RequestSizeLimitFilter ProductService"
found=0
while IFS= read -r file; do
  name="$(basename "$file" .java)"
  case " $EXPECTED " in *" $name "*) ;; *) echo "  $file"; found=1 ;; esac
done < <(find src/main -name '*.java')
[ "$found" -eq 0 ] && echo "  (none)"
echo
echo "Next: Maven -> Update Project (force update) in your IDE, or run: mvn clean verify -U"
