package ru.vibeart.api.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.vibeart.api.models.enums.RoleEnum;
import ru.vibeart.api.security.JwtAuthenticationFilter;
import ru.vibeart.api.security.JwtTokenProvider;
import ru.vibeart.api.services.impl.CustomUserDetailsService;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param tokenProvider провайдер JWT-токенов
     * @param customUserDetailsService сервис загрузки пользователей
     */
    public SecurityConfig(JwtTokenProvider tokenProvider, CustomUserDetailsService customUserDetailsService) {
        this.tokenProvider = tokenProvider;
        this.customUserDetailsService = customUserDetailsService;
    }

    /**
     * Бин для шифрования паролей с помощью BCrypt.
     *
     * @return {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Предоставляет {@link AuthenticationManager}, используемый при аутентификации.
     *
     * @param authConfig объект конфигурации Spring Security
     * @return экземпляр {@link AuthenticationManager}
     * @throws Exception при ошибке инициализации
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * Убирает префикс "ROLE_" у GrantedAuthority.
     * Это позволяет использовать имена ролей без добавления ROLE_ вручную.
     *
     * @return {@link GrantedAuthorityDefaults}
     */
    @Bean
    public GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("");
    }

    /**
     * Основная цепочка фильтров безопасности.
     * Настраивает:
     * <ul>
     *   <li>Отключение CSRF;</li>
     *   <li>Stateless-сессии;</li>
     *   <li>Разрешённые маршруты;</li>
     *   <li>JWT-фильтр перед {@link UsernamePasswordAuthenticationFilter}.</li>
     * </ul>
     *
     * @param http объект {@link HttpSecurity}
     * @return настроенный {@link SecurityFilterChain}
     * @throws Exception при ошибке конфигурации
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(tokenProvider, customUserDetailsService);

        http
                // Отключение CSRF защиты (для JWT не требуется)
                .csrf(AbstractHttpConfigurer::disable)
                // Stateless сессии (сервер не хранит сессии)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/swagger-ui/**").permitAll()
                        .requestMatchers("/v3/api-docs/**").hasAuthority(RoleEnum.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
