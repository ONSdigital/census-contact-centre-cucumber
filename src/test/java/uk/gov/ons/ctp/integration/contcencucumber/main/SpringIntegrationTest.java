package uk.gov.ons.ctp.integration.contcencucumber.main;

import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import uk.gov.ons.ctp.integration.contcencucumber.main.service.ProductService;

@ContextConfiguration(
    classes = {SpringIntegrationTest.class, ProductService.class},
    loader = SpringBootContextLoader.class,
    initializers = ConfigFileApplicationContextInitializer.class)
@WebAppConfiguration
@SpringBootTest
@Import({
  uk.gov.ons.ctp.integration.contcencucumber.main.service.impl.ProductServiceImpl.class,
  uk.gov.ons.ctp.integration.common.product.ProductReference.class
})
@ActiveProfiles("test")
public class SpringIntegrationTest {}