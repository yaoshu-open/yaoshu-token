module.exports = {
  extends: [
    'stylelint-config-standard-scss',
    'stylelint-config-recommended-vue/scss',
    'stylelint-config-recess-order'
  ],
  rules: {
    'selector-class-pattern': null,
    'scss/at-import-partial-extension': null,
    'no-empty-source': null,
    'unit-no-unknown': [true, { ignoreUnits: ['upx', 'rpx'] }],
    // 单行声明块允许多声明（compact 布局类写法合法合理，强制多行致代码膨胀）
    'declaration-block-single-line-max-declarations': null,

    // === Design Token 强制规则（设计文档 §六）===
    // 零值不带单位（安全规则，无例外）
    'length-zero-no-unit': true,
    // 禁止 border-radius 硬编码 scale 值，强制 var(--ys-radius-*) token
    // warning 级别：引导而非阻塞（50%/9999px 圆形、复合角部值等合法保留由人工 review）
    // 禁止 box-shadow 硬编码黑色阴影值，强制 var(--ys-shadow-*) token
    // warning 级别：引导而非阻塞（HeroTerminalDemo 终端装饰阴影、focus 光晕等合法保留由人工 review）
    'declaration-property-value-disallowed-list': [
      {
        'border-radius': ['/\\b(4|6|8|12|16)px/', '/0\\.5rem/', '/0\\.75rem/', '/\\b1rem/'],
        'box-shadow': ['/rgb\\(0 0 0/', '/rgba\\(0,\\s*0,\\s*0/']
      },
      { severity: 'warning' }
    ]
  },
  overrides: [
    {
      // Token 定义文件是 token 源头，允许 hex 与 px 字面量定义
      files: ['src/styles/tokens/**/*.scss'],
      rules: {
        'declaration-property-value-disallowed-list': null
      }
    }
  ],
  ignoreFiles: ['dist', 'node_modules']
}
