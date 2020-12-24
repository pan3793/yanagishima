package yanagishima.servlet;

import lombok.RequiredArgsConstructor;
import yanagishima.config.YanagishimaConfig;
import yanagishima.util.SparkUtil;
import yanagishima.util.YarnUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static yanagishima.util.AccessControlUtil.sendForbiddenError;
import static yanagishima.util.AccessControlUtil.validateDatasource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HiveQueryDetailServlet {
    private final YanagishimaConfig yanagishimaConfig;

    @GetMapping(path = {"hiveQueryDetail", "sparkQueryDetail"})
    public void get(@RequestParam String datasource,
                    @RequestParam String engine,
                    @RequestParam(name = "id") Optional<String> idOptional,
                    @RequestParam(name = "user") Optional<String> hiveUser,
                    HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (yanagishimaConfig.isCheckDatasource() && !validateDatasource(request, datasource)) {
            sendForbiddenError(response);
            return;
        }
        String resourceManagerUrl = yanagishimaConfig.getResourceManagerUrl(datasource);
        if (engine.equals("hive")) {
            idOptional.ifPresent(id -> {
                if (id.startsWith("application_")) {
                    try {
                        response.sendRedirect(resourceManagerUrl + "/cluster/app/" + id);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    String userName = null;
                    if (yanagishimaConfig.isUseAuditHttpHeaderName()) {
                        userName = request.getHeader(yanagishimaConfig.getAuditHttpHeaderName());
                    } else {
                        if (hiveUser.isPresent()) {
                            userName = hiveUser.get();
                        }
                    }
                    Optional<Map> applicationOptional = YarnUtil.getApplication(resourceManagerUrl, id, userName, yanagishimaConfig.getResourceManagerBegin(datasource));
                    applicationOptional.ifPresent(application -> {
                        String applicationId = (String) application.get("id");
                        try {
                            response.sendRedirect(resourceManagerUrl + "/cluster/app/" + applicationId);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            });
        } else if (engine.equals("spark")) {
            String sparkWebUrl = yanagishimaConfig.getSparkWebUrl(datasource);
            if (idOptional.isPresent()) {
                String jobId = idOptional.get();
                try {
                    Integer.parseInt(jobId);
                    String sparkJdbcApplicationId = SparkUtil.getSparkJdbcApplicationId(sparkWebUrl);
                    response.sendRedirect(resourceManagerUrl + "/proxy/" + sparkJdbcApplicationId + "/jobs/job?id=" + jobId);
                } catch (NumberFormatException e) {
                    // we can't specify spark jobId when user pushes info button in Query List tab
                    response.sendRedirect(sparkWebUrl);
                }
            } else {
                response.sendRedirect(sparkWebUrl);
            }
        } else {
            throw new IllegalArgumentException(engine + " is illegal");
        }
    }
}
