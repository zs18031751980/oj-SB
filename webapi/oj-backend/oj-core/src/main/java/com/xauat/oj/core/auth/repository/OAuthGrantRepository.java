package com.xauat.oj.core.auth.repository;
import com.xauat.oj.core.auth.domain.OAuthGrant;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OAuthGrantRepository extends JpaRepository<OAuthGrant,String> { java.util.Optional<OAuthGrant> findByBindingHash(String bindingHash); }
