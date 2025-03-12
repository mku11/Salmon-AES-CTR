import typescriptEslint from "@typescript-eslint/eslint-plugin";
import _import from "eslint-plugin-import";
import { fixupPluginRules } from "@eslint/compat";
import globals from "globals";
import tsParser from "@typescript-eslint/parser";
import path from "node:path";
import { fileURLToPath } from "node:url";
import js from "@eslint/js";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
    baseDirectory: __dirname,
    recommendedConfig: js.configs.recommended,
    allConfig: js.configs.all
});

export default [{
    ignores: ["**/node_modules/**/*", "**/node_modules/**/*"],
}, ...compat.extends("eslint:recommended"), {
    plugins: {
        "@typescript-eslint": typescriptEslint,
        import: fixupPluginRules(_import),
    },
    files: ["*.ts"],
    languageOptions: {
        globals: {
            ...globals.browser,
            ...globals.node,
        },

        parser: tsParser,
        ecmaVersion: "latest",
        sourceType: "module",

        parserOptions: {
            project: "tsconfig.json",
            tsconfigRootDir: "./",
        },
    },

    rules: {
        "import/no-cycle": ["error", {
            maxDepth: 10,
            ignoreExternal: true,
        }],

        "@typescript-eslint/no-floating-promises": ["error"],
    },
}, {
    files: ["**/*.test.js", "**/*test_helper.js"],

    languageOptions: {
        globals: {
            ...globals.jest,
        },
    },
}];