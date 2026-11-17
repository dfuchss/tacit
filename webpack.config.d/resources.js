config.resolve.fallback = {
    crypto: false,
    fs: false,
    path: false,
    stream: false,
    buffer: false,
    os: false,
    url: false,
};

config.module.rules =
    [
        ...(config.module?.rules || []),
        {test: /\.wasm$/, type: "asset/resource"},
    ]

// Minification
if (config.mode === "production") {
    config.optimization = {
        minimize: false, // temporary disabled, because build fails with "Set maximum size exceeded"
        minimizer: [],
    };
} else {
    config.optimization = {
        minimize: false,
        minimizer: [],
    };
}

// Copy dependencies
const CopyPlugin = require("copy-webpack-plugin");
config.plugins.push(
    new CopyPlugin({
        patterns: [
            // Required by the pdfjs runtime.
            {from: "../../node_modules/pdfjs-dist/build/pdf.worker.mjs", to: "."},
        ],
    })
)

// Dev Server
if (config.devServer) {
    config.devServer.historyApiFallback = {
        disableDotRule: true,
        rewrites: [
            {from: /\/sso\.html(\/.*)/, to: ({request, parsedUrl, match}) => match[1]},
            {from: /\/sso\.html/, to: '/index.html'},
        ],
    }
    config.devServer.headers = {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Methods": "*",
        "Access-Control-Allow-Headers": "*"
    }
}
