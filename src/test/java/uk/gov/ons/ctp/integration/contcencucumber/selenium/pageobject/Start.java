package uk.gov.ons.ctp.integration.contcencucumber.selenium.pageobject;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Start {

  private WebDriver driver;

  public Start(WebDriver driver) {
    this.driver = driver;
    PageFactory.initElements(driver, this);
  }

  @FindBy(css = "#uac")
  private WebElement uacTextBox;

  //  @FindBy(css = "#iac1")
  //  private WebElement uacTextBox1;
  //
  //  @FindBy(css = "#iac2")
  //  private WebElement uacTextBox2;
  //
  //  @FindBy(css = "#iac3")
  //  private WebElement uacTextBox3;
  //
  //  @FindBy(css = "#iac4")
  //  private WebElement uacTextBox4;

  public void clickUacBox() {
    uacTextBox.click();
  }

  public void addTextToUac(String txtToAdd) {
    uacTextBox.sendKeys(txtToAdd);
  }

  //  public void clickUacBox1() {
  //    uacTextBox1.click();
  //  }
  //
  //  public void addTextToUac1(String txtToAdd) {
  //    uacTextBox1.sendKeys(txtToAdd);
  //  }
  //
  //  public void clickUacBox2() {
  //    uacTextBox2.click();
  //  }
  //
  //  public void addTextToUac2(String txtToAdd) {
  //    uacTextBox2.sendKeys(txtToAdd);
  //  }
  //
  //  public void clickUacBox3() {
  //    uacTextBox3.click();
  //  }
  //
  //  public void addTextToUac3(String txtToAdd) {
  //    uacTextBox3.sendKeys(txtToAdd);
  //  }
  //
  //  public void clickUacBox4() {
  //    uacTextBox4.click();
  //  }
  //
  //  public void addTextToUac4(String txtToAdd) {
  //    uacTextBox4.sendKeys(txtToAdd);
  //  }

  public void enterUac(String uac) {
    clickUacBox();
    addTextToUac(uac);
    //    clickUacBox2();
    //    addTextToUac2(uac2);
    //    clickUacBox3();
    //    addTextToUac3(uac3);
    //    clickUacBox4();
    //    addTextToUac4(uac4);
  }
}
