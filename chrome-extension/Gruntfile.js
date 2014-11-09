module.exports = function (grunt) {

    var DEBUG = false;

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),
        uglify: {
            options: {
                banner: '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n',
                beautify: DEBUG,
                mangle: !DEBUG
            },

            dist: {
                files: {
                    'build/popup.js': [
                        'bower_components/jquery/dist/jquery.js',
                        'bower_components/spin.js/spin.js',
                        'bower_components/spin.js/jquery.spin.js',
                        'bower_components/underscore/underscore.js',
                        'bower_components/backbone/backbone.js',
                        'src/popup.js'
                    ]
                }
            }
        },

        jshint: {
            all: ['Gruntfile.js', 'src/**/*.js']
        }
    });

    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-contrib-jshint');

    grunt.registerTask('default', function () {
        grunt.task.run(['jshint', 'uglify']);
        grunt.file.copy('assets/icon.png', 'build/icon.png');
        grunt.file.copy('assets/popup.html', 'build/popup.html');
        grunt.file.copy('assets/popup.css', 'build/popup.css');
        grunt.file.copy('manifest.json', 'build/manifest.json');
    });

};