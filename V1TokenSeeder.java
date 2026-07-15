import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.security.oauth2.provider.token.store.redis.RedisTokenStore;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.egov.infra.admin.master.entity.User;
import org.egov.infra.admin.master.entity.Role;
import org.egov.infra.config.security.authentication.userdetail.CurrentUser;
import java.util.*;

public class V1TokenSeeder {
    public static void main(String[] args) throws Exception {
        JedisConnectionFactory factory = new JedisConnectionFactory();
        factory.setHostName("bpa-redis");
        factory.setPort(6379);
        factory.afterPropertiesSet();

        RedisTokenStore tokenStore = new RedisTokenStore(factory);

        User user = new User();
        java.lang.reflect.Method setIdMethod = User.class.getDeclaredMethod("setId", Long.class);
        setIdMethod.setAccessible(true);
        setIdMethod.invoke(user, 1L);
        user.setUsername("admin2");
        user.setTenantId("pb.amritsar");
        user.setName("Admin User");
        user.setActive(true);
        
        Set<Role> roles = new HashSet<Role>();
        Role role = new Role();
        role.setName("SUPERUSER");
        roles.add(role);
        Role role2 = new Role();
        role2.setName("BPA_BUILDER");
        roles.add(role2);
        Role role3 = new Role();
        role3.setName("BPA_VERIFIER");
        roles.add(role3);
        user.setRoles(roles);

        CurrentUser currentUser = new CurrentUser(user);

        OAuth2Request request = new OAuth2Request(
            Collections.<String, String>emptyMap(), "egov-user-client", 
            currentUser.getAuthorities(), true, 
            Collections.singleton("read"), Collections.<String>emptySet(), 
            null, Collections.<String>emptySet(), Collections.<String, java.io.Serializable>emptyMap()
        );

        UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(
            currentUser, "N/A", currentUser.getAuthorities()
        );

        OAuth2Authentication authentication = new OAuth2Authentication(request, userAuth);
        DefaultOAuth2AccessToken token = new DefaultOAuth2AccessToken("5a0b3170-33b7-4406-acde-c2e144172d9e");
        
        tokenStore.storeAccessToken(token, authentication);
        System.out.println("Seeded token successfully into bpa-redis!");
    }
}
