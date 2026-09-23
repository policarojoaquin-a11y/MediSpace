import com.microsoft.playwright.*;

/** Fase 7/8 QA — verifica los colores REALES renderizados (getComputedStyle) de los badges de
 * estado contra la paleta de la propuesta original (docs/Entrega... "1.4 Interfaces"). */
public class PaletteCheck {
    public static void main(String[] args) {
        String qaUsersPassword = requireEnv("QA_USERS_PASSWORD");
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            page.navigate("http://localhost:8080/");
            page.waitForSelector("#login-form");
            page.fill("#email", "qa.admin1@qa.test");
            page.fill("#password", qaUsersPassword);
            page.click("#btn-login");
            page.waitForURL("**/app.html", new Page.WaitForURLOptions().setTimeout(10000));
            page.waitForTimeout(1200);

            // Filtrar a datos QA antes de inspeccionar (regla de privacidad)
            page.locator("input[placeholder*='Buscar']").first().fill("QA_");
            page.waitForTimeout(500);

            String[] classes = {"badge-activo", "badge-inactivo"};
            for (String cls : classes) {
                Object result = page.evaluate(
                    "(cls) => { const el = document.querySelector('.' + cls); if (!el) return null; " +
                    "const s = getComputedStyle(el); return { bg: s.backgroundColor, color: s.color }; }",
                    cls
                );
                System.out.println(cls + " -> " + result);
            }
            context.close();
            browser.close();
        }
    }

    private static String requireEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) throw new IllegalStateException("Falta " + key + " -- corre '. qa\\load-env.ps1' primero.");
        return v;
    }
}
