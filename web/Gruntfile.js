module.exports = function (grunt) {

    var DEBUG = false;
    var banner = '/*! <%= pkg.name %> <%= grunt.template.today("yyyy-mm-dd") %> */\n';
    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),

        uglify: {
            options: {
                banner: banner,
                beautify: DEBUG,
                mangle: !DEBUG
            },

            dist: {
                files: {
                    'knapsack/assets/js/dist/knapsack.js': [
                        'bower_components/jquery/dist/jquery.js',
                        'bower_components/bootstrap/dist/js/bootstrap.js',
                        'knapsack/assets/js/*.js'
                    ]
                }
            }
        },

        jshint: {
            all: ['Gruntfile.js', 'knapsack/assets/js/*.js']
        },

        cssmin: {
            add_banner: {
                options: {
                    banner: banner
                },
                files: {
                    'knapsack/assets/css/dist/knapsack.css': [
                        'bower_components/bootstrap/dist/css/bootstrap.css',
                        'bower_components/bootstrap/dist/css/bootstrap-theme.css',
                        'knapsack/assets/css/*.css'
                    ]
                }
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-uglify');
    grunt.loadNpmTasks('grunt-contrib-jshint');
    grunt.loadNpmTasks('grunt-contrib-cssmin');

    grunt.registerTask('default', ['jshint', 'uglify', 'cssmin']);
};