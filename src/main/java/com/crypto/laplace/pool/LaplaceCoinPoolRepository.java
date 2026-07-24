package com.crypto.laplace.pool; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface LaplaceCoinPoolRepository extends JpaRepository<LaplaceCoinPoolEntity,String>{ List<LaplaceCoinPoolEntity> findByStateIn(Collection<LaplaceCoinPoolState> states); }
