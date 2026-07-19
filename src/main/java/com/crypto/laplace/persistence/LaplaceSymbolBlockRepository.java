package com.crypto.laplace.persistence;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface LaplaceSymbolBlockRepository extends JpaRepository<LaplaceSymbolBlockEntity,String> {
 @Query("select count(b)>0 from LaplaceSymbolBlockEntity b where b.symbol=:symbol and b.blockedUntil>:now")
 boolean existsActive(@Param("symbol") String symbol,@Param("now") Instant now);
}
