# Templates

These templates can be downloaded from the web interface at CloudRF.com or your SOOTHSAYER server. Save your settings as a template then look for the download link in the top left next to the template select box.

## CPU and GPU 

The "engine" parameter in the template determines whether it is CPU (2) or GPU (1). If you do not have a GPU plan or server, you will need to use engine = 2.

A GPU server can model LiDAR coverage faster and perform multisite calculations.

## Example template

```
{
    "version": "CloudRF-API-v3.8.3",
    "reference": "https://cloudrf.com/documentation/developer/swagger-ui/",
    "template": {
        "name": "FENIX_TALON_2.5G_BW4_SNR4",
        "service": "CloudRF https://api.cloudrf.com",
        "created_at": "2023-05-03T17:28:56+00:00",
        "owner": 34858,
        "bom_value": 0
    },
    "site": "RadioA",
    "network": "TALON",
    "engine": 1,
    "coordinates": 1,
    "transmitter": {
        "lat": 51.858204,
        "lon": -2.251795,
        "alt": 2,
        "frq": 2450,
        "txw": 1,
        "bwi": 4,
        "powerUnit": "W"
    },
    "receiver": {
        "lat": 0,
        "lon": 0,
        "alt": 1,
        "rxg": 2,
        "rxs": 2
    },
    "feeder": {
        "flt": 1,
        "fll": 0,
        "fcc": 0
    },
    "antenna": {
        "mode": "template",
        "txg": 2.15,
        "txl": 0,
        "ant": 39,
        "azi": 0,
        "tlt": 0,
        "hbw": 1,
        "vbw": 1,
        "fbr": 2.15,
        "pol": "v"
    },
    "model": {
        "pm": 11,
        "pe": 2,
        "ked": 1,
        "rel": 50
    },
    "environment": {
        "clm": 0,
        "cll": 0,
        "clt": "Minimal.clt"
    },
    "output": {
        "units": "m",
        "col": "SNR4.dB",
        "out": 4,
        "ber": 1,
        "mod": 0,
        "nf": -108,
        "res": 2,
        "rad": 0.5
    }
}
```

## Reference

https://cloudrf.com/documentation/developer/

https://docs.cloudrf.com
