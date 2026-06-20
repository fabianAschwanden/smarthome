package fabianaschwanden.smarthome.adapter.in.security;

import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/**
 * Mappt IdP-Rollennamen (Client-Rollen mit App-Präfix, z.B. "smarthome-admin") auf interne,
 * stabile Rollen. {@code @RolesAllowed}, Frontend und Tests verwenden nur die internen Namen.
 */
@ApplicationScoped
public class IdpRoleMappingAugmentor implements SecurityIdentityAugmentor {

    private static final Map<String, String> IDP_TO_INTERNAL = Map.of(
            "smarthome-user", "user",
            "smarthome-admin", "admin");

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }
        var builder = QuarkusSecurityIdentity.builder(identity);
        identity.getRoles().forEach(idpRole -> {
            var internal = IDP_TO_INTERNAL.get(idpRole);
            if (internal != null) {
                builder.addRole(internal);
            }
        });
        return Uni.createFrom().item(builder.build());
    }
}
