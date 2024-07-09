  config.set({
//    "singleRun": false,
//    "autoWatch": true,
    client: {
      mocha: {
        timeout : 15000
      }
    }
  });
//config.loggers.push({type: 'console'})
config.logLevel = config.LOG_DEBUG