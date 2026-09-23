import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import java.nio.file.Paths;
import java.util.*;

/**
 * Fase 8 QA — smoke test responsive (390px) + errores de consola, para los 3 roles reales
 * (login con credenciales QA). Standalone (no JUnit) para poder correrlo sin pasar por el
 * ciclo de vida de la suite de integracion. Screenshots solo de pantallas con datos QA,
 * guardadas localmente en qa/screenshots/ (no se publican).
 */
public class ResponsiveSmokeTest {
    record RoleCase(String email, String password, String rol, String expectedDefaultText) {}

    public static void main(String[] args) {
        // Credenciales desde variables de entorno (cargadas por ". qa\load-env.ps1" antes de
        // lanzar este programa desde la misma sesion de PowerShell) -- nunca hardcodeadas acá.
        String qaUsersPassword = requireEnv("QA_USERS_PASSWORD");
        String cardioPassword = requireEnv("QA_DR_CARDIO_PASSWORD");
        List<RoleCase> roles = List.of(
            new RoleCase("qa.gerente@qa.test", qaUsersPassword, "GERENTE", "Dashboard"),
            new RoleCase("qa.admin1@qa.test", qaUsersPassword, "ADMINISTRATIVO", "Pacientes"),
            new RoleCase("qa.dr.cardio@qa.test", cardioPassword, "MEDICO", "Agenda")
        );

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));

            for (RoleCase rc : roles) {
                List<String> consoleErrors = new ArrayList<>();
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setViewportSize(390, 844));
                Page page = context.newPage();
                page.onConsoleMessage(msg -> {
                    if ("error".equals(msg.type())) consoleErrors.add(msg.text());
                });
                page.onPageError(err -> consoleErrors.add("PAGE ERROR: " + err));

                page.navigate("http://localhost:8080/");
                page.waitForSelector("#login-form");
                page.fill("#email", rc.email());
                page.fill("#password", rc.password());
                page.click("#btn-login");
                page.waitForURL("**/app.html", new Page.WaitForURLOptions().setTimeout(10000));
                page.waitForTimeout(1200); // deja terminar el render inicial / primeras llamadas API

                // ADMINISTRATIVO aterriza en Pacientes con la lista COMPLETA (datos reales incluidos).
                // Filtramos a "QA_" antes de la captura -- regla de privacidad: solo pantallas con
                // datos QA en las capturas (nunca la lista sin filtrar).
                if ("ADMINISTRATIVO".equals(rc.rol())) {
                    Locator search = page.locator("input[placeholder*='Buscar']").first();
                    search.fill("QA_");
                    page.waitForTimeout(500);
                }

                String screenshotPath = "qa/screenshots/390px_" + rc.rol().toLowerCase() + ".png";
                page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(screenshotPath)).setFullPage(true));

                boolean bodyVisible = page.locator("body").isVisible();
                int htmlWidth = ((Number) page.evaluate("document.documentElement.scrollWidth")).intValue();
                boolean noHorizontalOverflow = htmlWidth <= 390 + 5; // pequeño margen de tolerancia

                System.out.println("=== " + rc.rol() + " (" + rc.email() + ") ===");
                System.out.println("  screenshot: " + screenshotPath);
                System.out.println("  body visible: " + bodyVisible);
                System.out.println("  scrollWidth=" + htmlWidth + "px -> sin overflow horizontal: " + noHorizontalOverflow);
                System.out.println("  errores de consola: " + consoleErrors.size());
                for (String e : consoleErrors) System.out.println("    - " + e);

                // Botones/targets clicables >= 44px. Separo .btn (la clase que global.css promete
                // 44px de alto) de "todos los <button>" (incluye iconos chicos de acciones de
                // tabla, que no necesariamente deberian contar como target primario).
                int[] statsAll = contarChicos(page, "button:visible");
                int[] statsBtn = contarChicos(page, ".btn:visible");
                System.out.println("  <button> (todos) < 44px: " + statsAll[0] + " de " + statsAll[1]);
                System.out.println("  .btn (clase CSS que promete 44px) < 44px: " + statsBtn[0] + " de " + statsBtn[1]);
                if ("MEDICO".equals(rc.rol())) {
                    // Diagnostico: que son realmente los .btn chicos? (solo en una pagina, alcanza)
                    List<ElementHandle> btns = page.querySelectorAll(".btn:visible");
                    int shown = 0;
                    for (ElementHandle b : btns) {
                        BoundingBox box = b.boundingBox();
                        if (box != null && box.height > 0 && box.height < 44 && shown < 8) {
                            String cls = (String) b.getAttribute("class");
                            String txt = b.innerText().trim();
                            System.out.println("    chico: h=" + box.height + " class=[" + cls + "] text=[" + txt + "]");
                            shown++;
                        }
                    }
                }

                context.close();
            }
            browser.close();
            System.out.println("\n=== Smoke test responsive completo ===");
        }
    }

    private static String requireEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + key
                + " -- corre '. qa\\load-env.ps1' en la misma sesion de PowerShell antes de lanzar java.");
        }
        return v;
    }

    private static int[] contarChicos(Page page, String selector) {
        List<ElementHandle> els = page.querySelectorAll(selector);
        int chico = 0;
        for (ElementHandle b : els) {
            BoundingBox box = b.boundingBox();
            if (box != null && box.height > 0 && box.height < 44) chico++;
        }
        return new int[]{chico, els.size()};
    }
}
