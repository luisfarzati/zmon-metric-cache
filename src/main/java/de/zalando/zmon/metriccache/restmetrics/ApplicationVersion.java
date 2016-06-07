package de.zalando.zmon.metriccache.restmetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by jmussler on 05.12.15.
 */
public class ApplicationVersion {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationVersion.class);

    protected String applicationId;
    protected String applicationVersion;

    protected List<ServiceInstance> instances = new ArrayList<>(4);

    private static int N = 120;

    public ApplicationVersion(String applicationId, String applicationVersion) {
        this.applicationId = applicationId;
        this.applicationVersion = applicationVersion;
    }

    public void cleanUp() {
        int i = instances.size() - 1;
        long now = System.currentTimeMillis();

        // remove elements from the end
        while (i >= 0) {
            long ts = instances.get(i).getMaxTimestamp();
            if ((now - ts) > 1000 * 60 * 60 * 240) { // keep instances around for 240 minutes
                LOG.info("Removing old instance: {}", instances.get(i).instanceId);
                instances.remove(i);
            }
            --i;
        }
    }

    public Collection<String> getTrackedEndpoints() {
        Set<String> endpoints = new HashSet<>();

        for (ServiceInstance i : instances) {
            for (Endpoint ep : i.endpoints) {
                endpoints.add(ep.path + "|" + ep.method);
            }
        }

        return endpoints;
    }

    public VersionResult getData(long maxTs) {
        maxTs = (maxTs - (N * 60000));
        VersionResult result = new VersionResult();

        Set<String> eps = new HashSet<>();
        Set<Integer> codes = new HashSet<>();

        for (ServiceInstance i : instances) {
            for (Endpoint e : i.endpoints) {
                eps.add(e.path + "|" + e.method);
                for (DataSeries d : e.series) {
                    codes.add(d.statusCode);
                }
            }
        }

        for (String ep : eps) {
            EpResult epr = new EpResult(ep.split("\\|")[0], ep.split("\\|")[1]);
            result.endpoints.put(ep, epr);

            for (int code : codes) {

                List<DataSeries> series = new ArrayList<>();
                for (ServiceInstance i : instances) {
                    for (Endpoint e : i.endpoints) {
                        if (!ep.equals(e.path + "|" + e.method)) {
                            continue;
                        }
                        for (DataSeries d : e.series) {
                            if (!(d.statusCode == code)) {
                                continue;
                            }
                            series.add(d);
                        }
                    }
                }

                List<EpPoint> points = new ArrayList<>(N);

                for (int i = 0; i < N; ++i) {
                    double rate = 0;
                    double latency99th = 0;
                    double latencyMedian = 0;
                    double latency75th = 0;
                    double maxLatency = 0;
                    double minLatency = Double.MAX_VALUE;
                    double maxRate = 0;
                    long tsMax = 0;
                    int n = 0;
                    boolean partial = false;

                    // Looping over different application instances here
                    for (DataSeries s : series) {
                        // assume that the TS is written and thus up to date, otherwise data point is invalid
                        if (s.ts[i] > maxTs) {
                            rate += s.points[i][0];
                            latency99th += s.points[i][3];
                            latency75th += s.points[i][2];
                            latencyMedian += s.points[i][1];

                            tsMax = Math.max(tsMax, s.ts[i]);

                            maxLatency = Math.max(s.points[i][3], maxLatency);
                            minLatency = Math.min(s.points[i][3], minLatency);
                            maxRate = Math.max(s.points[i][0], maxRate);

                            n++;
                        } else {
                            partial = true;
                        }
                    }
                    if (n > 0) {
                        points.add(new EpPoint(tsMax, rate, latency99th / n, latency75th / n, latencyMedian / n, maxRate, maxLatency, minLatency, partial));
                    }
                }

                // sort for now, ideally we only need to change above loop
                Collections.sort(points, (EpPoint a, EpPoint b) -> Long.compare(a.ts, b.ts));
                epr.points.put(code, points);
            }
        }

        return result;
    }


    /* for now assume no concurrency issue on instance level here, as freq too low and data arrives per instance */
    public void addDataPoint(String id, String path, String method, int status, long ts, double rate, double latencyMedian, double latency75th, double latency99th) {
        ServiceInstance instance = null;
        for (ServiceInstance si : instances) {
            if (si.instanceId.equals(id)) {
                instance = si;
            }
        }
        if (null == instance) {
            instance = new ServiceInstance(id);
            synchronized (this) {
                instances.add(instance);
            }
        }

        Endpoint ep = null;
        for (Endpoint e : instance.endpoints) {
            if (e.path.equals(path) && e.method.equals(method)) {
                ep = e;
            }
        }
        if (null == ep) {
            ep = new Endpoint(path, method);
            instance.endpoints.add(ep);
        }

        DataSeries series = null;
        for (DataSeries ds : ep.series) {
            if (ds.statusCode == status) {
                series = ds;
            }
        }

        if (null == series) {
            series = new DataSeries(status);
            ep.series.add(series);
        }
        series.newEntry(ts, rate, latencyMedian, latency75th, latency99th);
    }
}
